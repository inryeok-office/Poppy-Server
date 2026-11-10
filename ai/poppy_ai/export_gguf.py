from __future__ import annotations

import argparse
import ctypes
import os
import subprocess
import sys
from pathlib import Path


def merge_adapter(base: str, adapter: Path, out: Path) -> Path:
    import torch
    from peft import PeftModel
    from transformers import AutoModelForCausalLM, AutoTokenizer

    model = AutoModelForCausalLM.from_pretrained(base, dtype=torch.bfloat16, device_map={"": "cpu"})
    model = PeftModel.from_pretrained(model, str(adapter)).merge_and_unload()
    model.save_pretrained(out, safe_serialization=True)
    AutoTokenizer.from_pretrained(base).save_pretrained(out)
    return out


def resolve_hf_dir(model: str) -> str:
    if Path(model).is_dir():
        return model
    from huggingface_hub import snapshot_download

    return snapshot_download(model)


def convert_f16(hf_dir: str, out_file: Path, llama_cpp_dir: Path) -> None:
    env = dict(os.environ, PYTHONPATH=str(llama_cpp_dir / "gguf-py"))
    subprocess.run(
        [sys.executable, str(llama_cpp_dir / "convert_hf_to_gguf.py"), hf_dir, "--outfile", str(out_file), "--outtype", "f16"],
        check=True,
        env=env,
    )


def quantize(src: Path, dst: Path, ftype_name: str = "LLAMA_FTYPE_MOSTLY_Q4_K_M") -> None:
    import llama_cpp

    params = llama_cpp.llama_model_quantize_default_params()
    params.ftype = getattr(llama_cpp, ftype_name)
    params.nthread = os.cpu_count() or 4
    rc = llama_cpp.llama_model_quantize(str(src).encode(), str(dst).encode(), ctypes.byref(params))
    if rc != 0:
        raise RuntimeError(f"quantize failed rc={rc}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--adapter")
    parser.add_argument("--out", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--llama-cpp", default=str(Path.home() / "poppy-ai/tools/llama.cpp"))
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    hf_dir = resolve_hf_dir(args.base)
    if args.adapter:
        hf_dir = str(merge_adapter(args.base, Path(args.adapter), out / "merged"))
    f16 = out / f"{args.name}-f16.gguf"
    q4 = out / f"{args.name}-q4_k_m.gguf"
    convert_f16(hf_dir, f16, Path(args.llama_cpp))
    quantize(f16, q4)
    f16.unlink()
    print(q4, q4.stat().st_size)


if __name__ == "__main__":
    main()
