
from __future__ import annotations

import math
import re
from typing import Any

LIMITS = {
    "repeatCount": {"min": 1, "max": 10},
    "moveDistance": {"min": 0.1, "max": 3, "step": 0.1},
    "waitSeconds": {"min": 1, "max": 10},
    "rotateDegree": {"min": 1, "max": 360},
}

AI_STEP_MAX_BLOCKS = 12
AI_INPUT_MAX_LENGTH = 100

ALLOWED_KINDS = (
    "moveForward",
    "move",
    "rotateLeft",
    "rotateRight",
    "wait",
    "stop",
    "standUp",
    "greet",
    "heart",
    "dance",
    "repeat",
)
SIMPLE_KINDS = ("stop", "standUp", "greet", "heart", "dance")

UNRECOGNIZED_REASON = "무슨 블록인지 못 알아들었어요. 다시 말해 볼래요?"
TOO_MANY_REASON = f"한 번에 만들 수 있는 블록은 {AI_STEP_MAX_BLOCKS}개까지예요. 조금 줄여서 말해 볼래요?"
EMPTY_REPEAT_REASON = "반복할 동작이 없어요. 반복 안에 움직임을 넣어서 말해 볼래요?"
NESTED_REPEAT_REASON = "반복 안에는 반복을 넣을 수 없어요."
SIT_REASON = "지금은 앉기 동작을 쓸 수 없어요."
VALUE_REASON = "숫자가 범위를 벗어났어요. 다시 말해 볼래요?"


class ValidationError(ValueError):
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


def _number(raw: Any) -> float | None:
    if isinstance(raw, bool):
        return None
    if isinstance(raw, (int, float)):
        value = float(raw)
    elif isinstance(raw, str):
        try:
            value = float(raw.strip()) if raw.strip() else 0.0
        except ValueError:
            return None
    elif raw is None:
        value = 0.0
    else:
        return None
    return value if math.isfinite(value) else None


def in_range(raw: Any, spec: dict) -> bool:
    value = _number(raw)
    if value is None or value < spec["min"] or value > spec["max"]:
        return False
    step = spec.get("step")
    if step is None:
        return float(value).is_integer()
    return abs(value / step - round(value / step)) < 1e-9


def _clean_number(raw: Any, integer: bool) -> int | float:
    value = _number(raw)
    assert value is not None
    if integer:
        return int(value)
    return int(value) if float(value).is_integer() else round(value, 6)


def count_structural(steps: list[dict]) -> int:
    return sum(1 + (count_structural(s["body"]) if s["kind"] == "repeat" else 0) for s in steps)


def _validate_one(raw: Any, inside_repeat: bool) -> dict:
    if not isinstance(raw, dict):
        raise ValidationError(UNRECOGNIZED_REASON)
    kind = raw.get("kind")
    if kind == "sit":
        raise ValidationError(SIT_REASON)
    if not isinstance(kind, str) or kind not in ALLOWED_KINDS:
        raise ValidationError(UNRECOGNIZED_REASON)
    if kind in ("moveForward", "move"):
        if not in_range(raw.get("distanceM"), LIMITS["moveDistance"]):
            raise ValidationError(VALUE_REASON)
        return {"kind": kind, "distanceM": _clean_number(raw["distanceM"], False)}
    if kind in ("rotateLeft", "rotateRight"):
        if not in_range(raw.get("degrees"), LIMITS["rotateDegree"]):
            raise ValidationError(VALUE_REASON)
        return {"kind": kind, "degrees": _clean_number(raw["degrees"], True)}
    if kind == "wait":
        if not in_range(raw.get("seconds"), LIMITS["waitSeconds"]):
            raise ValidationError(VALUE_REASON)
        return {"kind": "wait", "seconds": _clean_number(raw["seconds"], True)}
    if kind in SIMPLE_KINDS:
        return {"kind": kind}
    if inside_repeat:
        raise ValidationError(NESTED_REPEAT_REASON)
    if not in_range(raw.get("count"), LIMITS["repeatCount"]):
        raise ValidationError(VALUE_REASON)
    body_raw = raw.get("body")
    if not isinstance(body_raw, list) or not body_raw:
        raise ValidationError(EMPTY_REPEAT_REASON)
    body = []
    for child in body_raw:
        step = _validate_one(child, True)
        if step["kind"] == "repeat":
            raise ValidationError(NESTED_REPEAT_REASON)
        body.append(step)
    return {"kind": "repeat", "count": _clean_number(raw["count"], True), "body": body}


def validate_ai_steps(value: Any) -> list[dict]:
    if not isinstance(value, list) or not value:
        raise ValidationError(UNRECOGNIZED_REASON)
    steps = [_validate_one(raw, False) for raw in value]
    if count_structural(steps) > AI_STEP_MAX_BLOCKS:
        raise ValidationError(TOO_MANY_REASON)
    return steps


def is_valid_steps(value: Any) -> bool:
    try:
        validate_ai_steps(value)
        return True
    except ValidationError:
        return False


_WS = re.compile(r"\s+")


def sanitize_input(raw: str) -> str:
    out = "".join(" " if ord(ch) <= 0x1F or ord(ch) == 0x7F else ch for ch in raw)
    return _WS.sub(" ", out).strip()[:AI_INPUT_MAX_LENGTH]


def normalize_utterance(raw: str) -> str:
    out = "".join(" " if ord(ch) <= 0x1F or ord(ch) == 0x7F else ch for ch in raw)
    return _WS.sub(" ", out).strip()
