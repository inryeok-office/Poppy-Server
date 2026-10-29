# 실행 요청 Robot 선택 v1

Issue #123 설계 1번(실행 요청에 선택적 Robot 지정)의 Server 구현이다. 지정하지 않으면 기존 동작과 같다.

## 요청

`POST /api/v1/sessions/{sessionId}/executions`

```json
{ "blockVersion": 3, "robotAlias": "GO2EDU002" }
```

- `robotAlias`는 선택 필드다. 체험자에게는 Robot ID 대신 `GET /api/v1/robots/availability`가 주는 alias만 노출한다.
- 공백 문자열이면 `COMMON_400`.
- 활성(`active=true`) Robot 중 alias가 정확히 하나 일치해야 한다. 없거나, 비활성이거나, 같은 alias가 둘 이상이면 `ROBOT_NOT_FOUND`(404)이며 Execution을 만들지 않는다(alias에는 DB unique 제약이 없다).
- OFFLINE·UNAVAILABLE·사용 중인 Robot은 거부하지 않는다. QUEUED로 두고 그 Robot이 배정 가능해지면 배정한다.
- 일치한 Robot ID는 `executions.requested_robot_id`(V21)에 저장한다.

## 배정

- 지정 실행: 후보는 지정 Robot 하나뿐이다. 배정 조건(active·ONLINE·READY·비점유·capability 일치)은 기존과 같다.
- 미지정 실행: 기존과 같이 ID 순서 후보 중 첫 Robot.

## 대기열 (`QueueDispatchService`)

`queuedAt`, `id` 오름차순으로 QUEUED 실행을 차례로 시도한다.

- 지정 실행이 배정되지 않으면 건너뛰고 다음 실행을 시도한다. 즉 지정 실행은 **Robot별 FIFO**로 기다리고, 다른 Robot을 쓰는 뒤 실행을 막지 않는다.
- 미지정 실행이 배정되지 않으면 그 tick을 멈춘다. 지정 실행이 없으면 기존 head-of-line 동작과 완전히 같다.
- 동시 dispatch로 이미 배정된 실행에서 `IllegalStateException`이 나면 건너뛴다. 아직 QUEUED인데 예외가 나면 그대로 던진다(기존 동작 유지).

## 범위 밖

- `queuePosition`(실행 상태 조회)은 지금처럼 전체 QUEUED 기준 순번이다. 지정 Robot 기준 대기 수는 아직 계산하지 않는다.
- Client 선택 UI.
