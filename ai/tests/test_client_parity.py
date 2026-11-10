import json
from pathlib import Path

import pytest

from poppy_ai.blocks import ValidationError, sanitize_input, validate_ai_steps
from poppy_ai.simulator import count_program_blocks, describe_heading, plan_motion

FIXTURES = json.loads((Path(__file__).parent / "fixtures" / "client_parity.json").read_text(encoding="utf-8"))


def _chain(program):
    return [{"kind": "start"}, *program, {"kind": "end"}]


@pytest.mark.parametrize("case", FIXTURES["motion"], ids=lambda c: json.dumps(c["program"], ensure_ascii=False)[:60])
def test_plan_motion_matches_client(case):
    plan = plan_motion(_chain(case["program"]))
    expected = case["expected"]
    assert plan.total_meters == pytest.approx(expected["totalMeters"], abs=1e-9)
    assert plan.total_seconds == pytest.approx(expected["totalSeconds"], abs=1e-9)
    assert plan.final_posture == expected["finalPosture"]
    assert plan.heading_degrees == expected["headingDegrees"]
    assert plan.violation == expected["violation"]
    assert [s["node"]["kind"] for s in plan.steps] == expected["executedKinds"]
    assert len(plan.path) == len(expected["path"])
    for got, want in zip(plan.path, expected["path"]):
        assert got == pytest.approx(tuple(want), abs=1e-9)
    if expected["failurePoint"] is None:
        assert plan.failure_point is None
    else:
        assert plan.failure_point == pytest.approx(tuple(expected["failurePoint"]), abs=1e-9)
    assert count_program_blocks(_chain(case["program"])) == expected["structuralBlocks"]


@pytest.mark.parametrize("case", FIXTURES["validate"], ids=lambda c: json.dumps(c["input"], ensure_ascii=False)[:60])
def test_validate_matches_client(case):
    expected = case["expected"]
    try:
        steps = validate_ai_steps(case["input"])
    except ValidationError as error:
        assert expected["ok"] is False
        assert error.reason == expected["reason"]
        return
    assert expected["ok"] is True
    assert steps == expected["steps"]


@pytest.mark.parametrize("case", FIXTURES["sanitize"], ids=lambda c: repr(c["input"])[:40])
def test_sanitize_matches_client(case):
    assert sanitize_input(case["input"]) == case["expected"]


@pytest.mark.parametrize("case", FIXTURES["describe"], ids=lambda c: str(c["input"]))
def test_describe_heading_matches_client(case):
    assert describe_heading(case["input"]) == case["expected"]


@pytest.mark.parametrize("case", FIXTURES["planner"], ids=lambda c: c["input"][:30])
def test_rule_planner_matches_client(case):
    from poppy_ai.rules import plan_from_utterance

    expected = case["expected"]
    got = plan_from_utterance(case["input"])
    if not expected["ok"]:
        assert got is None
    else:
        assert got == expected["draft"]["steps"]
