from __future__ import annotations


def _num(value: float) -> str:
    return str(int(value)) if float(value).is_integer() else f"{value:.1f}"


def _phrase(step: dict) -> str:
    kind = step["kind"]
    if kind == "moveForward":
        return f"앞으로 {_num(step['distanceM'])}m 가고"
    if kind == "move":
        return f"뒤로 {_num(step['distanceM'])}m 가고"
    if kind == "rotateLeft":
        return f"왼쪽으로 {step['degrees']}° 돌고"
    if kind == "rotateRight":
        return f"오른쪽으로 {step['degrees']}° 돌고"
    if kind == "wait":
        return f"{step['seconds']}초 기다리고"
    if kind == "repeat":
        inner = " ".join(_phrase(s) for s in step["body"])
        return f"({inner}) {step['count']}번 반복하고"
    return {"stop": "멈추고", "standUp": "일어서고", "greet": "인사하고", "heart": "하트 하고", "dance": "춤추고"}[kind]


def explain(steps: list[dict]) -> str:
    text = " ".join(_phrase(s) for s in steps)
    if text.endswith("고"):
        text = text[:-1] + "는"
    sentence = f"{text} 블록을 만들었어요."
    if len(sentence) > 200:
        return f"블록 {len(steps)}개로 이루어진 초안을 만들었어요."
    return sentence
