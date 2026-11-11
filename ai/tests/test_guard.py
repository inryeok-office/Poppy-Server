from poppy_ai.guard import cap_total_distance, is_actionable, total_distance
from poppy_ai.simulator import simulate_steps


def test_actionable_utterances():
    for utterance in ["앞으로 가", "춤춰", "두 걸음 걷고 인사해 줘", "왼쪽으로 90도 돌아", "하트"]:
        assert is_actionable(utterance)


def test_non_actionable_utterances():
    for utterance in ["밥 먹자", "앉아", "가", "", "졸려"]:
        assert not is_actionable(utterance)


def test_cap_keeps_program_within_limit_and_simulation():
    steps = [{"kind": "moveForward", "distanceM": 2.5}, {"kind": "wait", "seconds": 3}, {"kind": "move", "distanceM": 2}]
    capped = cap_total_distance(steps)
    assert total_distance(capped) <= 4.0
    assert steps[0]["distanceM"] == 2.5
    assert simulate_steps(capped).passed


def test_cap_leaves_short_program_unchanged():
    steps = [{"kind": "moveForward", "distanceM": 1}, {"kind": "greet"}]
    assert cap_total_distance(steps) is steps
