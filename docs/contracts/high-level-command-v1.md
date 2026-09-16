# High-level Command Protocol v1

High-level Command Protocol은 Block Program과 Robot SDK 사이의 Server 내부 normalized contract입니다. 이 버전은 Server 모델과 직렬화 계약만 정의하며 Agent delivery나 실제 Robot command를 포함하지 않습니다.

## Envelope

```json
{
  "protocolVersion": 1,
  "commands": []
}
```

`commands`는 실행 순서의 flat array입니다. `protocolVersion`은 현재 `1`만 지원합니다. START/END만 있는 프로그램은 compiler 결과로 빈 command 목록을 가질 수 있습니다.

## Command

모든 command는 다음 필드를 가집니다.

```json
{
  "sequence": 0,
  "sourceBlockId": "move-1",
  "type": "MOVE",
  "parameters": {
    "direction": "FORWARD",
    "distanceMeters": 1.0
  }
}
```

`sequence`는 0부터 시작하는 contiguous 실행 순서이며, `sourceBlockId`는 원본 BlockInstance id입니다. REPEAT expansion에서는 동일 sourceBlockId가 여러 sequence에 나타날 수 있습니다.

## Command types

| Type | Parameters |
| --- | --- |
| WAIT | `durationSeconds`: number |
| MOVE | `direction`: `FORWARD` 또는 `BACKWARD`, `distanceMeters`: number |
| TURN | `direction`: `LEFT` 또는 `RIGHT`, `angleDegrees`: number |
| STOP | `{}` |
| POSTURE | `posture`: `SIT` 또는 `STAND` |
| PRESET | `presetCode`: string |

Block mapping은 `BlockProgramCompiler`에서 적용합니다.

- WAIT → WAIT
- MOVE_FORWARD/MOVE_BACKWARD → MOVE
- TURN_LEFT/TURN_RIGHT → TURN
- STOP → STOP
- SIT/STAND → POSTURE
- PRESET → PRESET
- START/END → command 없음
- REPEAT → children를 count만큼 flat expansion

이번 계약에는 speed, velocity, pose, joint, torque, Unitree method name 같은 low-level 또는 vendor-specific 값이 없습니다. PRESET은 아직 실기기 whitelist가 확정되지 않아 실제 code 목록을 정의하지 않습니다.

Parser와 serializer는 unknown field, unsupported version, 잘못된 parameter type, numeric string coercion, sequence 위반을 허용하지 않습니다.

`requiredCapabilities`, Robot matching, Agent parser와 실행은 후속 범위입니다.
