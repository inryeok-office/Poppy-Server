from __future__ import annotations

DEFAULT_HINT = "블록을 다시 한 번 살펴봐요. 시작부터 종료까지 순서대로 잘 이어졌는지 확인해 볼까요?"


def hint_for(code: str | None, block_number: int | None, message: str | None) -> str:
    if code == "exceeds-safe-zone":
        if block_number:
            return f"{block_number}번째 블록에서 로봇이 안전한 공간 밖으로 나가요. 이동 거리를 줄이거나 방향을 바꿔 볼까요?"
        return "로봇이 안전한 공간 밖으로 나가요. 이동 거리를 줄이거나 방향을 바꿔 볼까요?"
    if code == "exceeds-time":
        return "프로그램이 너무 길어서 시간 안에 못 끝나요. 블록 수나 반복 횟수를 줄여 볼까요?"
    if code == "moves-while-sitting":
        return "로봇이 앉은 채로 움직이려고 해요. 그 앞에 일어서기 블록을 넣어 볼까요?"
    if code == "invalid-values":
        return "블록에 들어간 숫자가 허용 범위를 벗어났어요. 빨간 칸의 숫자를 범위 안으로 바꿔 볼까요?"
    return DEFAULT_HINT
