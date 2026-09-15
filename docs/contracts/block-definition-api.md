# Block Definition Metadata API

`GET /api/v1/blocks`는 Block Program v1에서 사용할 수 있는 정적 Block Definition catalog를 반환한다. 이 endpoint는 Session 소유 데이터가 아니므로 Session Token 없이 조회할 수 있다.

응답은 기존 `ApiResponse` 형식을 사용하며, `data.schemaVersion`은 `1`이다. `data.blocks`에는 다음 12개 BlockType이 안정적인 순서로 포함된다.

```text
START, WAIT, REPEAT, END,
MOVE_FORWARD, MOVE_BACKWARD, TURN_LEFT, TURN_RIGHT, STOP,
SIT, STAND, PRESET
```

각 정의는 `type`, `displayName`, `category`, `parameters`, `available`, `freeModeAllowed`를 포함한다. parameter 정의는 `name`, `type`, `unit`, `required`, `min`, `max`, `options`를 포함한다.

현재 실기기 검증이 완료되지 않은 안전 범위와 PRESET whitelist는 metadata로 확정하지 않는다. 따라서 parameter `min`·`max`는 `null`이고 PRESET `options`는 빈 배열이다. PRESET은 현재 `available: false`, `freeModeAllowed: false`로 표시한다.
