from __future__ import annotations

import argparse
import json
import statistics
import time
from pathlib import Path

from .blocks import ValidationError, validate_ai_steps
from .prompt import compact
from .rules import plan_from_utterance
from .simulator import simulate_steps


def load(path: Path, limit: int = 0) -> list[dict]:
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    return rows[:limit] if limit else rows


def score(rows: list[dict], predict) -> tuple[dict, list[dict]]:
    details = []
    for row in rows:
        steps, raw, latency = predict(row["utterance"])
        valid = steps is not None
        exact = valid and compact(steps) == compact(row["steps"])
        sim = valid and simulate_steps(steps).passed
        details.append({"utterance": row["utterance"], "gold": row["steps"], "raw": raw, "valid": valid, "exact": exact, "sim": sim, "latency_ms": latency})
    n = len(details)
    latencies = sorted(d["latency_ms"] for d in details)
    summary = {
        "n": n,
        "json_valid": round(sum(d["valid"] for d in details) / n, 4),
        "exact_match": round(sum(d["exact"] for d in details) / n, 4),
        "sim_pass": round(sum(d["sim"] for d in details) / n, 4),
        "latency_p50_ms": round(statistics.median(latencies), 1),
        "latency_p95_ms": round(latencies[min(n - 1, int(n * 0.95))], 1),
    }
    return summary, details


def rules_predictor():
    def predict(utterance: str):
        started = time.perf_counter()
        steps = plan_from_utterance(utterance)
        latency = (time.perf_counter() - started) * 1000
        try:
            steps = validate_ai_steps(steps) if steps is not None else None
        except ValidationError:
            steps = None
        return steps, json.dumps(steps, ensure_ascii=False), latency

    return predict


def model_predictor(engine, constrained: bool):
    def predict(utterance: str):
        gen = engine.generate(utterance, constrained=constrained)
        return gen.steps, gen.raw, gen.latency_ms

    return predict


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--method", required=True, help="rules | gguf:<path>")
    parser.add_argument("--label", required=True)
    parser.add_argument("--sets", nargs="+", required=True, help="name=path.jsonl")
    parser.add_argument("--out", required=True)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--modes", default="constrained,free")
    args = parser.parse_args()

    runs = []
    if args.method == "rules":
        runs.append(("rules", rules_predictor()))
    else:
        from .engine import DraftEngine

        engine = DraftEngine(args.method.split(":", 1)[1])
        for mode in args.modes.split(","):
            runs.append((mode, model_predictor(engine, mode == "constrained")))

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    results = []
    for set_spec in args.sets:
        name, path = set_spec.split("=", 1)
        rows = load(Path(path), args.limit)
        for mode, predict in runs:
            summary, details = score(rows, predict)
            entry = {"label": args.label, "mode": mode, "set": name, **summary}
            results.append(entry)
            print(json.dumps(entry, ensure_ascii=False), flush=True)
            with (out / f"{args.label}-{mode}-{name}.jsonl").open("w", encoding="utf-8") as f:
                for d in details:
                    f.write(json.dumps(d, ensure_ascii=False) + "\n")
    with (out / "results.jsonl").open("a", encoding="utf-8") as f:
        for entry in results:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
