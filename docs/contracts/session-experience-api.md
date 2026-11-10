# Session 체험 모드 API

Client 미션 모드(Poppy-Client #53)가 Session 에 고른 체험 모드와 미션을 저장하고, 서버가 그 미션의 `allowedBlocks` 를 블록 저장과 시뮬레이션 통과 단계에서 강제하기 위한 계약이다. 조회는 기존 `GET /api/v1/sessions/{sessionId}` 의 `mode`/`missionId` 필드를 그대로 쓴다.

## PATCH /api/v1/sessions/{sessionId}

`X-Session-Token` 이 필요하다.

요청:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `mode` | string | `MISSION` / `FREE`, 필수 |
| `missionId` | UUID \| null | `MISSION` 이면 필수, `FREE` 이면 생략하거나 `null` |

응답 `data`:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sessionId` | UUID | |
| `mode` | string | 저장된 모드 |
| `missionId` | UUID \| null | 저장된 미션 |
| `blockVersion` | number | 현재 블록 버전(변하지 않음) |

오류:

| 상황 | 코드 | 상태 |
| --- | --- | --- |
| `mode` 누락, `MISSION` 인데 `missionId` 없음, `FREE` 인데 `missionId` 있음 | `COMMON_400` (`fieldErrors[].field`) | 400 |
| catalog 에 없는 `missionId` | `MISSION_NOT_FOUND` | 404 |
| token 누락·불일치 / 만료 Session | `SESSION_TOKEN_INVALID` / `SESSION_EXPIRED` | 401 / 410 |
| 진행 중(종료되지 않은) Execution 이 있음 | `EXECUTION_SESSION_ACTIVE` | 409 |

모드나 미션이 실제로 바뀌면 그 Session 의 기존 simulation pass 를 모두 지운다. 다른 규칙으로 통과한 pass 로 실행되는 것을 막기 위해서이며, Client 는 모드 변경 뒤 현재 블록을 다시 시뮬레이션해야 실행을 요청할 수 있다. 같은 `mode`/`missionId` 로 다시 요청하면 아무것도 바꾸지 않는다.

## allowedBlocks 검증

Session 이 `MISSION` 이면 다음 두 단계에서 미션의 `allowedBlocks` 와 `START`/`END` 밖의 블록을 거부한다. `REPEAT` 의 `children` 까지 검사한다.

- `POST /api/v1/sessions/{sessionId}/block-revisions`
- `POST /api/v1/sessions/{sessionId}/simulation-passes` (FREE 에서 저장한 블록을 MISSION 으로 바꾼 뒤 통과시키는 경우)

거부 응답은 기존 `BLOCK_PROGRAM_INVALID`(400)이고, 위반 블록마다 `fieldErrors` 를 하나씩 담는다.

```json
{ "field": "blocks[1]", "reason": "BLOCK_NOT_ALLOWED_IN_MISSION: TURN_LEFT is not allowed in the Mission" }
```

`mode` 가 `null`(미선택)이거나 `FREE` 이면 이 검증을 하지 않는다.

## 범위 밖

미션 완료 판정(Issue #119 의 4번)은 정책 확정 후 별도 Issue 에서 다룬다.
