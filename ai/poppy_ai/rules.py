from __future__ import annotations

import re

from .blocks import LIMITS

KOREAN_COUNT_WORD = {"한": 1, "두": 2, "세": 3, "네": 4, "다섯": 5, "여섯": 6, "일곱": 7, "여덟": 8, "아홉": 9, "열": 10}


def _js_round(value: float) -> int:
    import math

    return math.floor(value + 0.5)


def _clamp(value: float, spec: dict) -> tuple[float, bool]:
    v = value
    clamped = False
    if v < spec["min"]:
        v, clamped = spec["min"], True
    if v > spec["max"]:
        v, clamped = spec["max"], True
    step = spec.get("step")
    if step is not None:
        rounded = round(_js_round(v / step) * step, 6)
        if abs(rounded - v) > 1e-9:
            clamped = True
        v = rounded
    else:
        rounded = _js_round(v)
        if rounded != v:
            clamped = True
        v = rounded
    if float(v).is_integer():
        v = int(v)
    return v, clamped


def _split(text: str) -> list[str]:
    return [s.strip() for s in re.split(r",|그리고|다음에", text) if s.strip()]


def _repeat_count(segment: str) -> tuple[int | None, str]:
    m = re.search(r"(\d+)\s*번", segment)
    if m:
        return int(m.group(1)), segment.replace(m.group(0), "", 1).strip()
    m = re.search(r"(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\s*번", segment)
    if m:
        return KOREAN_COUNT_WORD[m.group(1)], segment.replace(m.group(0), "", 1).strip()
    return None, segment


def _shape(segment: str):
    if re.search("네모", segment):
        return [{"kind": "repeat", "count": 4, "body": [{"kind": "moveForward", "distanceM": 1}, {"kind": "rotateLeft", "degrees": 90}]}]
    if re.search("세모", segment):
        return [{"kind": "repeat", "count": 3, "body": [{"kind": "moveForward", "distanceM": 1}, {"kind": "rotateLeft", "degrees": 120}]}]
    if re.search(r"반\s*바퀴", segment):
        return [{"kind": "rotateRight", "degrees": 180}]
    if re.search(r"한\s*바퀴", segment):
        return [{"kind": "repeat", "count": 4, "body": [{"kind": "rotateRight", "degrees": 90}]}]
    return None


def _number(text: str) -> float | None:
    try:
        return float(text)
    except ValueError:
        return None


def _rotate(segment: str):
    m = re.search(r"(왼쪽|오른쪽)(?:으로)?\s*(?:([\d.]+)\s*(?:도|°))?", segment)
    if not m:
        return None
    degrees = _number(m.group(2)) if m.group(2) else 90
    if degrees is None:
        return None
    value, _ = _clamp(degrees, LIMITS["rotateDegree"])
    return [{"kind": "rotateLeft" if m.group(1) == "왼쪽" else "rotateRight", "degrees": value}]


def _wait(segment: str):
    m = re.search(r"([\d.]+)\s*초\s*(?:동안\s*)?기다", segment)
    if not m or _number(m.group(1)) is None:
        return None
    value, _ = _clamp(float(m.group(1)), LIMITS["waitSeconds"])
    return [{"kind": "wait", "seconds": value}]


def _simple(segment: str):
    for pattern, kind in (("인사", "greet"), ("하트", "heart"), ("춤", "dance"), ("일어서", "standUp"), ("(멈춰|정지)", "stop")):
        if re.search(pattern, segment):
            return [{"kind": kind}]
    return None


def _single(text: str) -> tuple[list[dict], bool]:
    if "앉" in text:
        return [], True
    count, rest = _repeat_count(text)
    target = rest if rest else text
    parsed = _shape(target) or _rotate(target) or _wait(target) or _simple(target)
    if not parsed:
        return [], False
    if count is None or any(s["kind"] == "repeat" for s in parsed):
        return parsed, True
    value, _ = _clamp(count, LIMITS["repeatCount"])
    return [{"kind": "repeat", "count": value, "body": parsed}], True


def _segment(segment: str) -> tuple[list[dict], bool]:
    m = re.search(r"(?:(앞으로|뒤로)\s*)?([\d.]+)\s*(cm|m|칸)", segment)
    if m and _number(m.group(2)) is not None:
        meters = float(m.group(2))
        if m.group(3) == "cm":
            meters /= 100
        if m.group(3) == "칸":
            meters *= 0.5
        value, _ = _clamp(meters, LIMITS["moveDistance"])
        step = {"kind": "move" if m.group(1) == "뒤로" else "moveForward", "distanceM": value}
        remainder = segment[m.end():].strip()
        rest, _ = _single(remainder) if remainder else ([], False)
        return [step, *rest], True
    return _single(segment)


def plan_from_utterance(utterance: str) -> list[dict] | None:
    steps: list[dict] = []
    understood = False
    for segment in _split(utterance):
        result, ok = _segment(segment)
        understood = understood or ok
        steps.extend(result)
    return steps if understood and steps else None
