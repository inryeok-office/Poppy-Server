# Robot 가용 상태 API

`GET /api/v1/robots/availability`는 체험자 화면이 "로봇 준비됨 / 사용 중 / 연결 안 됨" 배지를 그리기 위한 읽기 전용 API다(Issue #123). 인증 없이 호출할 수 있으며, 관리자 API(`/api/v1/admin/robots`)와 달리 운영 정보는 내보내지 않는다.

## 응답

```json
{
  "success": true,
  "data": {
    "availableCount": 1,
    "total": 2,
    "robots": [
      { "alias": "GO2EDU001", "status": "READY" },
      { "alias": "GO2EDU002", "status": "OFFLINE" }
    ]
  },
  "error": null
}
```

| 필드 | 설명 |
| --- | --- |
| `availableCount` | `status`가 `READY`인 Robot 수 |
| `total` | 활성(`active=true`) Robot 수 |
| `robots[].alias` | Robot 별칭. alias 오름차순 |
| `robots[].status` | `READY` / `BUSY` / `OFFLINE` / `UNAVAILABLE` |

Robot ID, Agent ID, IP, 배터리, firmware, capability, 현재 Execution ID는 응답에 포함하지 않는다.

## status 계산

heartbeat가 갱신하는 기존 Robot 상태(`connectionStatus`, `operationStatus`, `currentExecutionId`)만으로 계산하며, 위에서부터 먼저 맞는 값을 쓴다.

1. `connectionStatus = OFFLINE` → `OFFLINE` (heartbeat 90초 초과 시 `RobotHeartbeatTimeoutService`가 OFFLINE 처리)
2. `operationStatus != READY` → `UNAVAILABLE` (Agent가 physical 비활성·BLOCKED 등으로 보고한 경우)
3. `currentExecutionId != null` → `BUSY`
4. 그 외 → `READY`

비활성화(`active=false`)된 Robot은 `robots`와 `total`에서 모두 제외한다.

`READY`는 배정 조건(`ExecutionAllocationService`)과 같은 기준이지만, capability 일치 여부는 따지지 않는다. 따라서 `READY`여도 특정 블록 구성이 요구하는 capability가 없으면 실행이 QUEUED로 남을 수 있다.
