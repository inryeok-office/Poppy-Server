from __future__ import annotations

import argparse
import hashlib
import json
import random
from pathlib import Path

from .blocks import AI_INPUT_MAX_LENGTH, count_structural, is_valid_steps
from .simulator import simulate_steps

MAX_TOTAL_MOVE_M = 4.0

SINO = {1: "일", 2: "이", 3: "삼", 4: "사", 5: "오", 6: "육", 7: "칠", 8: "팔", 9: "구", 10: "십"}
NATIVE = {1: "한", 2: "두", 3: "세", 4: "네", 5: "다섯", 6: "여섯", 7: "일곱", 8: "여덟", 9: "아홉", 10: "열"}
DEGREE_WORDS = {90: "구십", 180: "백팔십", 45: "사십오", 30: "삼십", 60: "육십", 120: "백이십", 360: "삼백육십", 270: "이백칠십"}
COMMON_DEGREES = [90, 90, 90, 45, 30, 60, 120, 180, 135, 270, 360, 15, 20]
COMMON_DISTANCES = [1, 1, 1, 2, 0.5, 0.5, 1.5, 3, 0.3, 0.2, 2.5, 0.8, 1.2]


def fmt_num(value: float) -> str:
    return str(int(value)) if float(value).is_integer() else f"{value:.1f}"


def say_distance(rng: random.Random, d: float) -> str:
    choices = [f"{fmt_num(d)}m", f"{fmt_num(d)}미터", f"{fmt_num(d)} 미터", f"{fmt_num(d)}m"]
    if d < 1:
        choices += [f"{int(round(d * 100))}cm", f"{int(round(d * 100))}센티", f"{int(round(d * 100))}센티미터"]
    if d == 0.5:
        choices += ["반 미터", "한 칸"]
    if float(d).is_integer() and int(d) in SINO:
        choices += [f"{SINO[int(d)]} 미터", f"{SINO[int(d)]}미터"]
    if d in (1.0, 1.5, 2.0, 2.5, 3.0) and not float(d).is_integer():
        choices += [f"{SINO[int(d)]} 미터 반"]
    if float(d * 2).is_integer() and d >= 1 and float(d).is_integer():
        choices += [f"{NATIVE[int(d * 2)]} 칸"]
    return rng.choice(choices)


def say_degrees(rng: random.Random, deg: int) -> str:
    choices = [f"{deg}도", f"{deg} 도", f"{deg}°"]
    if deg in DEGREE_WORDS:
        choices.append(f"{DEGREE_WORDS[deg]} 도")
    return rng.choice(choices)


def say_count(rng: random.Random, n: int) -> str:
    return rng.choice([f"{n}번", f"{n} 번", f"{NATIVE[n]} 번", f"{NATIVE[n]}번"])


def say_seconds(rng: random.Random, s: int) -> str:
    return rng.choice([f"{s}초", f"{s} 초", f"{s}초"])


FINAL_ENDINGS = ["", " 줘", " 주세요", "요"]


def phrase(rng: random.Random, step: dict) -> tuple[str, str]:
    kind = step["kind"]
    if kind == "moveForward":
        d = say_distance(rng, step["distanceM"])
        options = [
            (f"앞으로 {d} 가고", f"앞으로 {d} 가"),
            (f"앞으로 {d} 이동하고", f"앞으로 {d} 이동해"),
            (f"{d} 앞으로 걸어가고", f"{d} 앞으로 걸어가"),
            (f"{d} 직진하고", f"{d} 직진해"),
            (f"{d} 전진하고", f"{d} 전진해"),
            (f"앞으로 {d} 걷고", f"앞으로 {d} 걸어"),
            (f"{d} 앞으로 가고", f"{d} 앞으로 가"),
        ]
    elif kind == "move":
        d = say_distance(rng, step["distanceM"])
        options = [
            (f"뒤로 {d} 가고", f"뒤로 {d} 가"),
            (f"뒤로 {d} 물러나고", f"뒤로 {d} 물러나"),
            (f"{d} 후진하고", f"{d} 후진해"),
            (f"뒤로 {d} 이동하고", f"뒤로 {d} 이동해"),
            (f"{d} 뒷걸음질하고", f"{d} 뒷걸음질해"),
        ]
    elif kind in ("rotateLeft", "rotateRight"):
        side = "왼쪽" if kind == "rotateLeft" else "오른쪽"
        turn = "좌회전" if kind == "rotateLeft" else "우회전"
        deg = step["degrees"]
        g = say_degrees(rng, deg)
        options = [
            (f"{side}으로 {g} 돌고", f"{side}으로 {g} 돌아"),
            (f"{g} {side}으로 회전하고", f"{g} {side}으로 회전해"),
            (f"{side}으로 {g} 회전하고", f"{side}으로 {g} 회전해"),
            (f"{side}으로 {g} 몸을 돌리고", f"{side}으로 {g} 몸을 돌려"),
        ]
        if deg == 90:
            options += [
                (f"{side}으로 돌고", f"{side}으로 돌아"),
                (f"{turn}하고", f"{turn}해"),
                (f"{side}으로 직각으로 돌고", f"{side}으로 직각으로 돌아"),
            ]
        if deg == 180:
            options += [(f"{side}으로 반 바퀴 돌고", f"{side}으로 반 바퀴 돌아")]
        if deg == 360:
            options += [(f"{side}으로 한 바퀴 돌고", f"{side}으로 한 바퀴 돌아")]
    elif kind == "wait":
        s = say_seconds(rng, step["seconds"])
        options = [
            (f"{s} 기다리고", f"{s} 기다려"),
            (f"{s} 동안 쉬고", f"{s} 동안 쉬어"),
            (f"{s} 가만히 있다가", f"{s} 가만히 있어"),
            (f"{s} 동안 기다렸다가", f"{s} 동안 기다려"),
            (f"{s} 쉬었다가", f"{s} 쉬어"),
        ]
    elif kind == "stop":
        options = [("멈추고", "멈춰"), ("정지하고", "정지해"), ("그만하고", "그만해"), ("끝내고", "끝내")]
    elif kind == "standUp":
        options = [("일어서고", "일어서"), ("일어나고", "일어나"), ("똑바로 서고", "똑바로 서")]
    elif kind == "greet":
        options = [("인사하고", "인사해"), ("손 흔들어 인사하고", "손 흔들어 인사해"), ("안녕 하고 인사하고", "안녕 하고 인사해"), ("반갑게 인사하고", "반갑게 인사해")]
    elif kind == "heart":
        options = [("하트 하고", "하트 해"), ("하트 만들고", "하트 만들어"), ("하트 포즈 하고", "하트 포즈 해"), ("사랑의 하트 하고", "사랑의 하트 해")]
    elif kind == "dance":
        options = [("춤추고", "춤춰"), ("춤을 추고", "춤을 춰"), ("신나게 춤추고", "신나게 춤춰"), ("댄스 하고", "댄스 해")]
    else:
        raise ValueError(kind)
    return rng.choice(options)


def phrase_repeat(rng: random.Random, step: dict, is_last: bool = True) -> tuple[str, str]:
    n = step["count"]
    body = step["body"]
    shape = shape_phrase(rng, step)
    if shape:
        return shape
    c = say_count(rng, n)
    inner_conn = [phrase(rng, s)[0] for s in body[:-1]]
    last_conn, last_final = phrase(rng, body[-1])
    inner = " ".join(inner_conn + [last_conn])
    inner_final = " ".join(inner_conn + [last_final])
    style = rng.randrange(4) if is_last or len(body) == 1 else rng.choice([1, 3])
    if style == 0:
        return f"{c} 반복해서 {inner}", f"{c} 반복해서 {inner_final}"
    if style == 1:
        core = inner_final
        return f"{core} 이걸 {c} 반복하고", f"{core} 이걸 {c} 반복해"
    if style == 2:
        return f"{c} {inner}", f"{c} {inner_final}"
    return f"{inner_final}, 이걸 {c} 하고", f"{inner_final}, 이걸 {c} 해"


def shape_phrase(rng: random.Random, step: dict) -> tuple[str, str] | None:
    body = step["body"]
    if len(body) != 2 or body[0]["kind"] != "moveForward" or body[1]["kind"] != "rotateLeft":
        return None
    d = say_distance(rng, body[0]["distanceM"])
    deg, n = body[1]["degrees"], step["count"]
    if deg == 90 and n == 4:
        name = rng.choice(["네모", "사각형", "정사각형", "네모 모양"])
    elif deg == 120 and n == 3:
        name = rng.choice(["세모", "삼각형", "세모 모양"])
    else:
        return None
    obj = name + ("를" if name in ("네모", "세모") else "을")
    if rng.random() < 0.5:
        return f"한 변이 {d}인 {obj} 그리고", f"한 변이 {d}인 {obj} 그려"
    return f"{d}씩 {obj} 그리며 걷고", f"{d}씩 {obj} 그리며 걸어"


CONNECTORS = [" ", " ", ", ", " 그리고 ", " 그 다음에 ", " 다음에 "]


def utterance_for(rng: random.Random, steps: list[dict]) -> str:
    parts = []
    for i, step in enumerate(steps):
        conn, final = phrase_repeat(rng, step, i == len(steps) - 1) if step["kind"] == "repeat" else phrase(rng, step)
        parts.append(final + rng.choice(FINAL_ENDINGS) if i == len(steps) - 1 else conn)
    text = parts[0]
    for part in parts[1:]:
        text += rng.choice(CONNECTORS) + part
    if rng.random() < 0.15:
        text = rng.choice(["로봇아, ", "뽀삐야 ", "강아지야 ", "포피, "]) + text
    if rng.random() < 0.1:
        text += rng.choice(["!", ".", "~"])
    return text.replace("요요", "요").replace("  ", " ")


LEAF_WEIGHTS = {
    "moveForward": 30,
    "move": 6,
    "rotateLeft": 16,
    "rotateRight": 12,
    "wait": 7,
    "stop": 2,
    "standUp": 3,
    "greet": 8,
    "heart": 7,
    "dance": 7,
}


def random_leaf(rng: random.Random, allow_stop: bool = True) -> dict:
    kinds = [k for k in LEAF_WEIGHTS if allow_stop or k != "stop"]
    kind = rng.choices(kinds, weights=[LEAF_WEIGHTS[k] for k in kinds])[0]
    if kind in ("moveForward", "move"):
        d = rng.choice(COMMON_DISTANCES) if rng.random() < 0.7 else round(rng.randint(1, 30) / 10, 1)
        return {"kind": kind, "distanceM": int(d) if float(d).is_integer() else d}
    if kind in ("rotateLeft", "rotateRight"):
        deg = rng.choice(COMMON_DEGREES) if rng.random() < 0.8 else rng.randint(1, 360)
        return {"kind": kind, "degrees": deg}
    if kind == "wait":
        return {"kind": "wait", "seconds": rng.randint(1, 10)}
    return {"kind": kind}


def random_body_leaf(rng: random.Random) -> dict:
    while True:
        leaf = random_leaf(rng, allow_stop=False)
        if leaf["kind"] != "standUp":
            return leaf


def random_shape(rng: random.Random) -> dict:
    if rng.random() < 0.6:
        side = rng.choice([0.5, 1, 1, 1.5, 2, 0.8, 0.3])
        return {"kind": "repeat", "count": 4, "body": [{"kind": "moveForward", "distanceM": side}, {"kind": "rotateLeft", "degrees": 90}]}
    side = rng.choice([0.5, 1, 1, 1.2, 0.8])
    return {"kind": "repeat", "count": 3, "body": [{"kind": "moveForward", "distanceM": side}, {"kind": "rotateLeft", "degrees": 120}]}


def random_program(rng: random.Random) -> list[dict]:
    n = rng.choices([1, 2, 3, 4, 5], weights=[22, 30, 26, 15, 7])[0]
    steps = []
    for i in range(n):
        r = rng.random()
        if r < 0.08:
            steps.append(random_shape(rng))
        elif r < 0.25:
            body = [random_body_leaf(rng) for _ in range(rng.choices([1, 2, 3], weights=[5, 4, 1])[0])]
            steps.append({"kind": "repeat", "count": rng.randint(2, 10), "body": body})
        else:
            steps.append(random_leaf(rng, allow_stop=(i == n - 1)))
    return steps


def total_move(steps: list[dict]) -> float:
    total = 0.0
    for s in steps:
        if s["kind"] == "repeat":
            total += s["count"] * total_move(s["body"])
        elif s["kind"] in ("moveForward", "move"):
            total += s["distanceM"]
    return total


def accept(steps: list[dict]) -> bool:
    return (
        is_valid_steps(steps)
        and count_structural(steps) <= 12
        and simulate_steps(steps).passed
        and total_move(steps) <= MAX_TOTAL_MOVE_M + 1e-9
    )


def split_of(utterance: str, val_ratio: float, test_ratio: float) -> str:
    h = int(hashlib.sha256(utterance.encode()).hexdigest()[:8], 16) / 0xFFFFFFFF
    if h < test_ratio:
        return "test"
    if h < test_ratio + val_ratio:
        return "val"
    return "train"


def canonical(steps: list[dict]) -> str:
    return json.dumps(steps, ensure_ascii=False, separators=(",", ":"))


def generate(n: int, seed: int, val_ratio: float = 0.08, test_ratio: float = 0.08) -> dict[str, list[dict]]:
    rng = random.Random(seed)
    seen_utterances: set[str] = set()
    seen_programs: dict[str, str] = {}
    splits: dict[str, list[dict]] = {"train": [], "val": [], "test": []}
    stats = {"sampled": 0, "rejected_sim": 0, "rejected_dup": 0, "rejected_len": 0}
    while sum(len(v) for v in splits.values()) < n:
        stats["sampled"] += 1
        steps = random_program(rng)
        if not accept(steps):
            stats["rejected_sim"] += 1
            continue
        utterance = utterance_for(rng, steps)
        if len(utterance) > AI_INPUT_MAX_LENGTH:
            stats["rejected_len"] += 1
            continue
        if utterance in seen_utterances:
            stats["rejected_dup"] += 1
            continue
        key = canonical(steps)
        split = seen_programs.get(key) or split_of(key, val_ratio, test_ratio)
        seen_programs[key] = split
        seen_utterances.add(utterance)
        splits[split].append({"utterance": utterance, "steps": steps})
    splits["_stats"] = [stats]
    return splits


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("-n", type=int, default=8000)
    parser.add_argument("--seed", type=int, default=20261110)
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    splits = generate(args.n, args.seed)
    stats = splits.pop("_stats")[0]
    for name, rows in splits.items():
        with (out / f"{name}.jsonl").open("w", encoding="utf-8") as f:
            for row in rows:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")
    summary = {"seed": args.seed, "n": args.n, **{k: len(v) for k, v in splits.items()}, **stats}
    (out / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
