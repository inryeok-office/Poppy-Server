from __future__ import annotations

import copy
import math

MAX_TOTAL_DISTANCE_M = 4.0
MIN_DISTANCE_M = 0.1

ACTION_WORDS = (
    "앞", "뒤", "가", "와", "걸", "걷", "걸음", "이동", "전진", "후진", "직진",
    "돌", "회전", "바퀴", "왼", "오른", "좌", "우", "도",
    "기다", "멈", "정지", "서", "일어",
    "인사", "안녕", "하트", "춤", "댄스",
    "반복", "번", "네모", "사각", "세모", "삼각", "모양",
    "미터", "센티", "m", "cm",
)
BLOCKED_WORDS = ("앉",)


def is_actionable(utterance: str) -> bool:
    text = utterance.replace(" ", "").lower()
    if len(text) < 2:
        return False
    if any(word in text for word in BLOCKED_WORDS):
        return False
    return any(word in text for word in ACTION_WORDS)


def total_distance(steps: list[dict]) -> float:
    total = 0.0
    for step in steps:
        if step.get("kind") in ("moveForward", "move"):
            total += float(step["distanceM"])
        elif step.get("kind") == "repeat":
            total += int(step["count"]) * total_distance(step.get("body", []))
    return total


def _scale(steps: list[dict], factor: float) -> None:
    for step in steps:
        if step.get("kind") in ("moveForward", "move"):
            scaled = math.floor(float(step["distanceM"]) * factor * 10 + 1e-9) / 10
            step["distanceM"] = max(MIN_DISTANCE_M, scaled)
        elif step.get("kind") == "repeat":
            _scale(step.get("body", []), factor)


def cap_total_distance(steps: list[dict], limit: float = MAX_TOTAL_DISTANCE_M) -> list[dict]:
    total = total_distance(steps)
    if total <= limit + 1e-9:
        return steps
    capped = copy.deepcopy(steps)
    _scale(capped, limit / total)
    return capped
