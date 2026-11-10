import json
from pathlib import Path

from poppy_ai.blocks import AI_INPUT_MAX_LENGTH, count_structural, validate_ai_steps
from poppy_ai.datagen import MAX_TOTAL_MOVE_M, generate, total_move
from poppy_ai.simulator import simulate_steps


def _all_steps(steps):
    for step in steps:
        yield step
        if step["kind"] == "repeat":
            yield from step["body"]


def test_generated_rows_respect_contract_and_simulator():
    splits = generate(400, seed=7)
    stats = splits.pop("_stats")[0]
    rows = [r for rows in splits.values() for r in rows]
    assert len(rows) == 400
    assert stats["sampled"] >= 400
    for row in rows:
        assert 0 < len(row["utterance"]) <= AI_INPUT_MAX_LENGTH
        assert validate_ai_steps(row["steps"]) == row["steps"]
        assert count_structural(row["steps"]) <= 12
        assert simulate_steps(row["steps"]).passed
        assert total_move(row["steps"]) <= MAX_TOTAL_MOVE_M + 1e-9
        assert all(s["kind"] != "sit" for s in _all_steps(row["steps"]))


def test_generation_is_reproducible_and_splits_do_not_share_programs():
    first = generate(300, seed=11)
    second = generate(300, seed=11)
    assert first == second
    programs = {name: {json.dumps(r["steps"], sort_keys=True) for r in first[name]} for name in ("train", "val", "test")}
    assert not programs["train"] & programs["test"]
    assert not programs["train"] & programs["val"]
    assert not programs["val"] & programs["test"]


def test_manual_test_set_is_valid_and_passes_simulation():
    path = Path(__file__).parent.parent / "data" / "manual_test.jsonl"
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    assert len(rows) >= 40
    for row in rows:
        assert validate_ai_steps(row["steps"]) == row["steps"]
        assert simulate_steps(row["steps"]).passed
