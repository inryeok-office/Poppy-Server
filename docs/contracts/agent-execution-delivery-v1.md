# Agent Execution Delivery v1

`GET /api/v1/internal/agents/{agentId}/executions/next?robotId={robotId}`는 배정된 Execution의 immutable 실행 snapshot을 Agent에 전달합니다.

## 응답

Execution이 있으면 `data.execution`은 다음 필드를 가집니다.

```json
{
  "executionId": "...",
  "robotId": "...",
  "status": "ASSIGNED",
  "protocolVersion": 1,
  "commandPayload": "{\"protocolVersion\":1,\"commands\":[...]}"
}
```

`commandPayload`는 Execution 생성 시 저장된 High-level Command Protocol v1 JSON 문자열입니다. Agent delivery 시 Block Revision을 다시 조회하거나 compile하지 않습니다.

실행 snapshot이 없는 legacy ASSIGNED Execution은 정상 delivery 대상이 아니며 `EXECUTION_DELIVERY_INVARIANT_VIOLATED` 오류로 처리합니다. 빈 command payload를 대신 생성하지 않습니다.

Execution이 없으면 기존 계약대로 `data.execution`은 `null`입니다.

## Agent cancellation status

Agent가 배정된 Execution의 lifecycle을 확인할 때는 다음 인증된 endpoint를
사용합니다.

`GET /api/v1/internal/agents/{agentId}/executions/{executionId}/status?robotId={robotId}`

응답의 `data`는 `executionId`, `robotId`, `status`를 포함합니다. `CANCELLED`는
terminal status이며 Agent status report에도 허용됩니다. Session 사용자의 cancel
요청은 `QUEUED`, `ASSIGNED`, `RUNNING` 상태에서 `CANCELLED`로 전환할 수 있고,
할당된 Robot 점유를 해제합니다. `COMPLETED`, `FAILED`, `CANCELLED` 상태는
terminal 상태이므로 이후의 stale report가 이를 덮어쓸 수 없습니다.
