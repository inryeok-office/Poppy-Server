# AI 도우미 API

체험자가 문장으로 블록 초안을 만들거나 시뮬레이션 실패 힌트를 받는 API다. Client `docs/ai-assistant.md` 계약을 따른다.
AI 는 블록 초안과 힌트 문장만 돌려준다. 실행 요청·로봇 명령 경로와 연결되지 않으며, 앉기(`sit`) 블록을 돌려주지 않는다.
Server 는 로컬 추론 서비스(`POPPY_AI_INFERENCE_URL`, `ai/` 의 `poppy_ai.server`)를 호출한다. 실패하면 503 을 돌려주고 Client 는 로컬 규칙으로 대신한다.
인증이 필요 없고, 요청 내용은 저장하거나 세션과 묶지 않는다(로그에는 실패 사유만 남긴다).

## POST /api/v1/ai/program-drafts

요청 `{ "utterance": "네모를 그리면서 걸어 줘" }`

- 제어 문자를 공백으로 바꾸고 연속 공백을 줄인 뒤 앞뒤 공백을 지운다.
- 정리 후 비었거나 100자를 넘으면 `400 AI_INPUT_INVALID`, 부적절한 단어가 있으면 `400 AI_INPUT_REJECTED`. 이 경우 추론 서비스를 호출하지 않는다.

응답 `200`

```json
{
  "success": true,
  "data": {
    "steps": [{ "kind": "repeat", "count": 4, "body": [{ "kind": "moveForward", "distanceM": 1 }, { "kind": "rotateLeft", "degrees": 90 }] }],
    "explanation": "(앞으로 1m 가고 왼쪽으로 90° 돌고) 4번 반복하는 블록을 만들었어요."
  },
  "error": null
}
```

추론 결과는 Client `validateAiSteps` 와 같은 규칙으로 다시 검증한다.

| kind | 필드 | 범위 |
| --- | --- | --- |
| `moveForward`, `move`(뒤로) | `distanceM` | 0.1~3, 0.1 단위 |
| `rotateLeft`, `rotateRight` | `degrees` | 1~360 정수 |
| `wait` | `seconds` | 1~10 정수 |
| `stop`, `standUp`, `greet`, `heart`, `dance` | 없음 | |
| `repeat` | `count`, `body` | count 1~10, body 1개 이상, 중첩 금지 |

- 구조적 블록 수(반복 1 + 몸통)는 12 이하. 모르는 필드는 버린다.
- 위반하면 결과를 전달하지 않고 `503 AI_UNAVAILABLE`.
- `explanation` 이 비었거나 200자를 넘거나 부적절하면 기본 문구로 바꾼다.

## POST /api/v1/ai/hints

요청 `{ "programLines": ["앞으로 3m 이동"], "violationCode": "exceeds-safe-zone", "violationMessage": "...", "violationBlockNumber": 2 }`

- `programLines` 최대 40줄·줄당 100자, `violationMessage` 300자 이하, `violationBlockNumber` 양수. 어기면 400.
- 응답 `200 { "data": { "message": "..." } }`. 메시지가 비었거나 200자 초과·부적절하면 503.

## 오류와 타임아웃

| 상황 | 응답 |
| --- | --- |
| `POPPY_AI_INFERENCE_URL` 미설정, 연결 실패, 추론 5xx, JSON 아님, 계약 위반 | `503 AI_UNAVAILABLE` |
| 연결 1초(`POPPY_AI_INFERENCE_CONNECT_TIMEOUT`), 요청 4초(`POPPY_AI_INFERENCE_TIMEOUT`) 초과 | `503 AI_UNAVAILABLE` |

Server 전체 응답은 5초 안에 끝난다. Client 는 503·네트워크 오류·타임아웃에서 로컬 planner/hint 로 대신한다.
