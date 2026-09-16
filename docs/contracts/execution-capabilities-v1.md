# Execution Capability v1

High-level Command Protocol v1의 실행 capability 요구사항과 Robot matching 규칙을 정의한다.

## Command 매핑

| CommandType | Required capability |
| --- | --- |
| `WAIT` | 없음 |
| `MOVE` | `COMMAND_MOVE` |
| `TURN` | `COMMAND_TURN` |
| `STOP` | `COMMAND_STOP` |
| `POSTURE` | `COMMAND_POSTURE` |
| `PRESET` | `COMMAND_PRESET` |

`START`, `END`, `REPEAT`는 compiler 결과인 Command Protocol에 포함되지 않으므로 capability를 요구하지 않는다.

## Robot matching

Robot은 required capability를 모두 보유해야 하며, capability의 `status`가 `VERIFIED`인 경우에만 실행 후보로 인정한다.

- `VERIFIED`: matching 가능
- `UNVERIFIED`: matching 불가
- `UNSUPPORTED`: matching 불가

Robot의 기존 capability code는 임의 문자열을 계속 허용한다. registry에 없는 Robot capability가 있어도 오류로 처리하지 않으며, required capability는 위 stable code registry에서만 계산한다.

`PRESET`의 실제 whitelist와 Robot 검증은 아직 확정되지 않았으므로 이 계약은 `COMMAND_PRESET`을 임의로 `VERIFIED` 처리하지 않는다.

이번 단계에서는 required capability 계산을 Queue allocation에 연결하지 않으며, Execution payload persistence와 Agent delivery도 변경하지 않는다.
