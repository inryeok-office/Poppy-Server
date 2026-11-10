from __future__ import annotations

import argparse
import json
import random
import subprocess
import threading
import time
from pathlib import Path

import torch
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    Trainer,
    TrainerCallback,
    TrainingArguments,
)

from .prompt import build_prompt, compact

GPU_PAUSE_CELSIUS = 90
GPU_RESUME_CELSIUS = 80


def gpu_temperature() -> int | None:
    try:
        out = subprocess.run(
            ["nvidia-smi", "--query-gpu=temperature.gpu", "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=10, check=True,
        )
        return int(out.stdout.strip().splitlines()[0])
    except (OSError, ValueError, subprocess.SubprocessError, IndexError):
        return None


class ThermalGuard(TrainerCallback):
    def __init__(self, every_steps: int = 10):
        self.every_steps = every_steps
        self.max_seen = 0
        self.paused_seconds = 0.0

    def on_step_end(self, args, state, control, **kwargs):
        if state.global_step % self.every_steps:
            return
        temp = gpu_temperature()
        if temp is None:
            return
        self.max_seen = max(self.max_seen, temp)
        if temp >= GPU_PAUSE_CELSIUS:
            print(f"[thermal] {temp}C >= {GPU_PAUSE_CELSIUS}C, pausing", flush=True)
            start = time.time()
            while (t := gpu_temperature()) is not None and t > GPU_RESUME_CELSIUS:
                time.sleep(15)
            self.paused_seconds += time.time() - start
            print(f"[thermal] resumed after {time.time() - start:.0f}s", flush=True)


class TimeBudget(TrainerCallback):
    def __init__(self, minutes: float):
        self.deadline = time.time() + minutes * 60

    def on_step_end(self, args, state, control, **kwargs):
        if time.time() > self.deadline:
            print("[budget] time budget reached, stopping", flush=True)
            control.should_training_stop = True


def load_rows(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def encode(tokenizer, row: dict, max_len: int) -> dict:
    prompt_ids = tokenizer(build_prompt(tokenizer, row["utterance"]), add_special_tokens=False).input_ids
    target_ids = tokenizer(compact(row["steps"]) + "<|im_end|>", add_special_tokens=False).input_ids
    ids = (prompt_ids + target_ids)[:max_len]
    labels = ([-100] * len(prompt_ids) + target_ids)[:max_len]
    return {"input_ids": ids, "labels": labels}


class Collator:
    def __init__(self, pad_id: int):
        self.pad_id = pad_id

    def __call__(self, batch):
        width = max(len(b["input_ids"]) for b in batch)
        ids = torch.full((len(batch), width), self.pad_id, dtype=torch.long)
        labels = torch.full((len(batch), width), -100, dtype=torch.long)
        mask = torch.zeros((len(batch), width), dtype=torch.long)
        for i, b in enumerate(batch):
            n = len(b["input_ids"])
            ids[i, :n] = torch.tensor(b["input_ids"])
            labels[i, :n] = torch.tensor(b["labels"])
            mask[i, :n] = 1
        return {"input_ids": ids, "labels": labels, "attention_mask": mask}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--data", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--epochs", type=float, default=2.0)
    parser.add_argument("--lr", type=float, default=2e-4)
    parser.add_argument("--rank", type=int, default=16)
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--grad-accum", type=int, default=2)
    parser.add_argument("--max-len", type=int, default=384)
    parser.add_argument("--max-train", type=int, default=0)
    parser.add_argument("--budget-minutes", type=float, default=85)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    data = Path(args.data)

    tokenizer = AutoTokenizer.from_pretrained(args.base)
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token
    train_rows = load_rows(data / "train.jsonl")
    if args.max_train:
        train_rows = train_rows[: args.max_train]
    val_rows = load_rows(data / "val.jsonl")[:300]
    train_set = [encode(tokenizer, r, args.max_len) for r in train_rows]
    val_set = [encode(tokenizer, r, args.max_len) for r in val_rows]

    quant = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16,
    )
    model = AutoModelForCausalLM.from_pretrained(args.base, quantization_config=quant, dtype=torch.bfloat16, device_map={"": 0})
    model = prepare_model_for_kbit_training(model, use_gradient_checkpointing=True)
    lora = LoraConfig(
        r=args.rank,
        lora_alpha=args.rank * 2,
        lora_dropout=0.05,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        task_type="CAUSAL_LM",
    )
    model = get_peft_model(model, lora)
    model.print_trainable_parameters()

    thermal = ThermalGuard()
    training_args = TrainingArguments(
        output_dir=str(out / "checkpoints"),
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch,
        per_device_eval_batch_size=args.batch,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        lr_scheduler_type="cosine",
        warmup_steps=20,
        logging_steps=10,
        eval_strategy="steps",
        eval_steps=200,
        save_strategy="no",
        bf16=True,
        optim="paged_adamw_8bit",
        report_to=[],
        seed=args.seed,
        dataloader_num_workers=0,
    )
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_set,
        eval_dataset=val_set,
        data_collator=Collator(tokenizer.pad_token_id),
        callbacks=[thermal, TimeBudget(args.budget_minutes)],
    )
    started = time.time()
    result = trainer.train()
    elapsed = time.time() - started
    metrics = trainer.evaluate()
    model.save_pretrained(out / "adapter")
    tokenizer.save_pretrained(out / "adapter")
    summary = {
        "base": args.base,
        "train_examples": len(train_set),
        "val_examples": len(val_set),
        "epochs": args.epochs,
        "lr": args.lr,
        "rank": args.rank,
        "batch": args.batch,
        "grad_accum": args.grad_accum,
        "train_seconds": round(elapsed, 1),
        "train_loss": result.training_loss,
        "eval_loss": metrics.get("eval_loss"),
        "global_step": trainer.state.global_step,
        "max_gpu_celsius": thermal.max_seen,
        "thermal_pause_seconds": round(thermal.paused_seconds, 1),
        "peak_gpu_mem_gb": round(torch.cuda.max_memory_allocated() / 1e9, 2),
    }
    (out / "train_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary), flush=True)


if __name__ == "__main__":
    main()
