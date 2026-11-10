from __future__ import annotations

import json
import time
from dataclasses import dataclass
from pathlib import Path

from .blocks import ValidationError, validate_ai_steps
from .grammar import STEPS_GBNF
from .prompt import chatml_prompt


def extract_json(raw: str):
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        pass
    start = raw.find("[")
    end = raw.rfind("]")
    if start == -1 or end <= start:
        raise json.JSONDecodeError("no array", raw, 0)
    value = json.loads(raw[start:end + 1])
    return value


@dataclass
class Generation:
    raw: str
    steps: list[dict] | None
    error: str | None
    latency_ms: float


def family_of(model_path: str) -> str:
    return "qwen3" if "qwen3" in Path(model_path).name.lower() else "qwen25"


class DraftEngine:
    def __init__(self, model_path: str, n_gpu_layers: int = -1, n_ctx: int = 1024, family: str | None = None):
        from llama_cpp import Llama, LlamaGrammar

        self.model_path = model_path
        self.family = family or family_of(model_path)
        self.llm = Llama(model_path=model_path, n_gpu_layers=n_gpu_layers, n_ctx=n_ctx, verbose=False, seed=0)
        self.grammar = LlamaGrammar.from_string(STEPS_GBNF, verbose=False)

    def generate(self, utterance: str, constrained: bool = True, max_tokens: int = 320) -> Generation:
        prompt = chatml_prompt(utterance, self.family)
        started = time.perf_counter()
        out = self.llm.create_completion(
            prompt,
            max_tokens=max_tokens,
            temperature=0.0,
            grammar=self.grammar if constrained else None,
            stop=["<|im_end|>", "<|endoftext|>"],
        )
        latency = (time.perf_counter() - started) * 1000
        raw = out["choices"][0]["text"].strip()
        try:
            steps = validate_ai_steps(extract_json(raw))
            return Generation(raw, steps, None, latency)
        except json.JSONDecodeError:
            return Generation(raw, None, "invalid-json", latency)
        except ValidationError as error:
            return Generation(raw, None, f"invalid-steps:{error.reason}", latency)
