from __future__ import annotations

import math
from dataclasses import dataclass, field

MOTION_TIMING = {
    "linearMetersPerSecond": 0.5,
    "turnDegreesPerSecond": 45,
    "postureSettleSeconds": 3,
    "presetSettleSeconds": 5,
}
ARENA = {"widthM": 6, "depthM": 6, "marginM": 0.4, "startXM": 5.6, "startYM": 0.4}
MAX_TOTAL_SECONDS = 120
EDGE_EPSILON = 1e-9
PRESET_KINDS = {"greet", "heart", "dance"}


@dataclass
class MotionPlan:
    steps: list[dict]
    total_meters: float
    total_seconds: float
    final_posture: str
    heading_degrees: int
    path: list[tuple[float, float]]
    failure_point: tuple[float, float] | None
    violation: dict | None = field(default=None)

    @property
    def passed(self) -> bool:
        return self.violation is None


def _inside(point: tuple[float, float]) -> bool:
    x, y = point
    w, d, m = ARENA["widthM"], ARENA["depthM"], ARENA["marginM"]
    return m - EDGE_EPSILON <= x <= w - m + EDGE_EPSILON and m - EDGE_EPSILON <= y <= d - m + EDGE_EPSILON


def _js_div(a: float, b: float) -> float:
    if b != 0:
        return a / b
    if a == 0 or math.isnan(a):
        return math.nan
    return math.copysign(math.inf, a) * math.copysign(1.0, b)


def _js_min(a: float, b: float) -> float:
    if math.isnan(a) or math.isnan(b):
        return math.nan
    return min(a, b)


def _exit_point(start: tuple[float, float], end: tuple[float, float]) -> tuple[float, float]:
    w, d, m = ARENA["widthM"], ARENA["depthM"], ARENA["marginM"]

    def axis_limit(s: float, e: float, low: float, high: float) -> float:
        if e > high:
            return _js_div(high - s, e - s)
        if e < low:
            return _js_div(low - s, e - s)
        return 1.0

    t = _js_min(axis_limit(start[0], end[0], m, w - m), axis_limit(start[1], end[1], m, d - m))
    return (start[0] + (end[0] - start[0]) * t, start[1] + (end[1] - start[1]) * t)


def _flatten(nodes: list[dict]) -> list[dict]:
    out: list[dict] = []
    for node in nodes:
        if node["kind"] == "repeat":
            for _ in range(int(node["count"])):
                out.extend(_flatten(node["body"]))
        else:
            out.append(node)
    return out


def _signed_meters(node: dict) -> float:
    if node["kind"] == "moveForward":
        return node["distanceM"]
    if node["kind"] == "move":
        return -node["distanceM"]
    return 0.0


def _cost(node: dict) -> tuple[float, float]:
    kind = node["kind"]
    if kind in ("move", "moveForward"):
        return node["distanceM"], node["distanceM"] / MOTION_TIMING["linearMetersPerSecond"]
    if kind in ("rotateLeft", "rotateRight"):
        return 0.0, node["degrees"] / MOTION_TIMING["turnDegreesPerSecond"]
    if kind in ("sit", "standUp"):
        return 0.0, MOTION_TIMING["postureSettleSeconds"]
    if kind == "wait":
        return 0.0, node["seconds"]
    return 0.0, MOTION_TIMING["presetSettleSeconds"] if kind in PRESET_KINDS else 0.0


def _needs_standing(node: dict) -> bool:
    return node["kind"] in ("move", "moveForward", "rotateLeft", "rotateRight") or node["kind"] in PRESET_KINDS


def _js_round(value: float) -> int:
    return math.floor(value + 0.5)


def plan_motion(chain: list[dict]) -> MotionPlan:
    flat = _flatten(chain)
    stop_at = next((i for i, n in enumerate(flat) if n["kind"] == "stop"), -1)
    executed = flat if stop_at == -1 else flat[: stop_at + 1] + [n for n in flat[stop_at + 1:] if n["kind"] == "end"]

    meters = 0.0
    seconds = 0.0
    posture = "standing"
    heading = 0.0
    position = (float(ARENA["startXM"]), float(ARENA["startYM"]))
    left_arena = False
    failure_point = None
    path = [position]
    violation = None
    steps = []
    for index, node in enumerate(executed):
        add_m, add_s = _cost(node)
        if violation is None and posture == "sitting" and _needs_standing(node):
            violation = {"index": index, "code": "moves-while-sitting"}
        meters += add_m
        seconds += add_s
        if node["kind"] == "sit":
            posture = "sitting"
        if node["kind"] == "standUp":
            posture = "standing"
        if node["kind"] == "rotateRight":
            heading += node["degrees"]
        if node["kind"] == "rotateLeft":
            heading -= node["degrees"]
        moved = _signed_meters(node)
        if math.isfinite(moved) and moved != 0 and not left_arena:
            radians = heading * math.pi / 180
            nxt = (position[0] + moved * math.sin(radians), position[1] + moved * math.cos(radians))
            if _inside(nxt):
                position = nxt
            else:
                left_arena = True
                position = _exit_point(position, nxt)
                if violation is None:
                    violation = {"index": index, "code": "exceeds-safe-zone"}
                    failure_point = position
            path.append(position)
        if violation is None and seconds > MAX_TOTAL_SECONDS + 1e-9:
            violation = {"index": index, "code": "exceeds-time"}
        steps.append({"node": node, "cumulativeMeters": meters, "cumulativeSeconds": seconds})

    rounded = _js_round(heading)
    heading_degrees = int(math.fmod(math.fmod(rounded, 360) + 360, 360))
    return MotionPlan(steps, meters, seconds, posture, heading_degrees, path, failure_point, violation)


def simulate_steps(steps: list[dict]) -> MotionPlan:
    return plan_motion([{"kind": "start"}, *steps, {"kind": "end"}])


def count_program_blocks(nodes: list[dict]) -> int:
    return sum(
        0 if n["kind"] in ("start", "end") else 1 + (count_program_blocks(n["body"]) if n["kind"] == "repeat" else 0)
        for n in nodes
    )


def describe_heading(degrees: float) -> str:
    h = int(math.fmod(math.fmod(_js_round(degrees), 360) + 360, 360))
    if h == 0:
        return "정면"
    if h == 180:
        return "뒤쪽"
    return f"오른쪽 {h}°" if h < 180 else f"왼쪽 {360 - h}°"
