from __future__ import annotations

import json

SYSTEM_PROMPT = (
    "너는 어린이용 로봇 블록 코딩 도우미다. 사용자의 한국어 문장을 블록 JSON 배열로만 바꾼다. "
    "블록: moveForward{distanceM 0.1~3, 0.1단위}, move(뒤로){distanceM}, rotateLeft{degrees 1~360}, "
    "rotateRight{degrees 1~360}, wait{seconds 1~10}, stop, standUp, greet(인사), heart(하트), dance(춤), "
    "repeat{count 1~10, body}. 앉기는 만들지 않는다. 반복 안에 반복 금지, 블록은 최대 12개. "
    "설명 없이 JSON 배열만 출력한다."
)


def compact(steps: list[dict]) -> str:
    return json.dumps(steps, ensure_ascii=False, separators=(",", ":"))


def messages_for(utterance: str) -> list[dict]:
    return [{"role": "system", "content": SYSTEM_PROMPT}, {"role": "user", "content": utterance}]


def build_prompt(tokenizer, utterance: str) -> str:
    kwargs = {"tokenize": False, "add_generation_prompt": True}
    try:
        return tokenizer.apply_chat_template(messages_for(utterance), enable_thinking=False, **kwargs)
    except TypeError:
        return tokenizer.apply_chat_template(messages_for(utterance), **kwargs)


def chatml_prompt(utterance: str, family: str) -> str:
    text = f"<|im_start|>system\n{SYSTEM_PROMPT}<|im_end|>\n<|im_start|>user\n{utterance}<|im_end|>\n<|im_start|>assistant\n"
    if family == "qwen3":
        text += "<think>\n\n</think>\n\n"
    return text
