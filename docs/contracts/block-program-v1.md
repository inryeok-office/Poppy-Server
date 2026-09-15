# Block Program v1

## Root

```json
{
  "schemaVersion": 1,
  "blocks": []
}
```

`schemaVersion`은 필수 정수이며 현재 `1`만 지원합니다. `blocks`는 직렬화된 실행 순서의 배열입니다.

## BlockInstance

```json
{
  "id": "move-1",
  "type": "MOVE_FORWARD",
  "parameters": {
    "distanceMeters": 1.0
  }
}
```

`id`는 프로그램 안에서 블록을 구분하는 비어 있지 않은 opaque 문자열입니다. `parameters`는 항상 객체이며, 파라미터가 없는 블록은 `{}`를 사용합니다. `REPEAT`만 `children` 배열을 가지며, 다른 블록의 `children`은 허용하지 않습니다.

## Block types

| Category | Block types |
| --- | --- |
| FLOW | `START`, `WAIT`, `REPEAT`, `END` |
| MOVEMENT | `MOVE_FORWARD`, `MOVE_BACKWARD`, `TURN_LEFT`, `TURN_RIGHT`, `STOP` |
| ACTION | `SIT`, `STAND`, `PRESET` |

## Parameters

| Block type | Parameter | JSON type |
| --- | --- | --- |
| `WAIT` | `durationSeconds` | number |
| `REPEAT` | `count` | integer |
| `MOVE_FORWARD`, `MOVE_BACKWARD` | `distanceMeters` | number |
| `TURN_LEFT`, `TURN_RIGHT` | `angleDegrees` | number |
| `PRESET` | `presetCode` | string enum value |

파라미터 이름과 타입은 엄격히 검사합니다. 문자열 숫자, 정수가 아닌 `count`, 알 수 없는 필드는 허용하지 않습니다. 아직 안전한 min/max와 `PRESET` whitelist는 확정하지 않았습니다.

## REPEAT

```json
{
  "id": "repeat-1",
  "type": "REPEAT",
  "parameters": { "count": 2 },
  "children": [
    {
      "id": "turn-1",
      "type": "TURN_LEFT",
      "parameters": { "angleDegrees": 90 }
    }
  ]
}
```

`children`의 순서는 실행 순서입니다. START/END 위치, 중복 id, 빈 REPEAT, nested REPEAT, 실행 한도와 파라미터 안전 범위는 후속 `BlockProgramValidator`의 책임입니다.

## Canonical example

```json
{
  "schemaVersion": 1,
  "blocks": [
    { "id": "start-1", "type": "START", "parameters": {} },
    { "id": "move-1", "type": "MOVE_FORWARD", "parameters": { "distanceMeters": 1.0 } },
    {
      "id": "repeat-1",
      "type": "REPEAT",
      "parameters": { "count": 2 },
      "children": [
        { "id": "turn-1", "type": "TURN_LEFT", "parameters": { "angleDegrees": 90.0 } },
        { "id": "wait-1", "type": "WAIT", "parameters": { "durationSeconds": 1.5 } }
      ]
    },
    { "id": "end-1", "type": "END", "parameters": {} }
  ]
}
```

이번 버전은 Block Revision validation 연결, `/api/v1/blocks`, compiler, Command Protocol, capability matching, Agent 연동을 포함하지 않습니다.
