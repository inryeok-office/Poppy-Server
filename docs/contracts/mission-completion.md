# 미션 완료 판정 (블록 구성 기준, 1차)

MISSION 모드 세션에서 요청한 실행이 `COMPLETED`가 되면, Server가 그 실행에 쓰인 블록 구성(`sessionId` + `blockVersion`의 revision)을 미션의 완료 규칙과 비교해 `missionResult`를 돌려준다(Issue #119).

**물리적 판정은 범위 밖이다.** 도착 지점·허용 반경·회전각 오차·실행 시간·코스 이탈처럼 로봇의 실제 위치나 센서 값이 필요한 조건은 판정하지 않는다. "로봇이 실행을 끝까지 마쳤다(COMPLETED)" + "블록 구성이 규칙을 만족한다"만 본다.

## 미션 저장

- `POST /api/v1/sessions/{sessionId}/executions` 시점에 세션 `mode`가 `MISSION`이면 `missionId`를 Execution에 함께 저장한다(`executions.mission_id`, V20).
- 실행 후에 세션 미션이 바뀌어도 이미 만든 Execution의 판정 대상 미션은 바뀌지 않는다.
- FREE·미선택 세션의 실행은 `mission_id`가 null이다.

## 응답

`GET /api/v1/executions/{executionId}`의 `data.missionResult`:

```json
{
  "missionId": "f3f450e4-8faf-4d2e-b2aa-6f76ac115412",
  "passed": false,
  "reasons": [
    { "code": "MISSING_REQUIRED_BLOCK", "detail": "MOVE_FORWARD" }
  ]
}
```

- `status`가 `COMPLETED`이고 `mission_id`가 있을 때만 값이 있고, 그 외에는 `null`이다(FAILED·CANCELLED 실행은 판정하지 않음).
- 결과를 저장하지 않고 조회 때마다 계산한다. 입력(고정된 revision과 정적 catalog 규칙)이 바뀌지 않으므로 결과도 같다.
- SSE 이벤트에는 넣지 않는다. Client는 COMPLETED 이벤트를 받은 뒤 위 GET으로 결과를 읽는다.

## 규칙 종류 (`MissionCompletionRule`)

| 규칙 | 실패 코드 | 판정 방식 |
| --- | --- | --- |
| `requiredBlocks` | `MISSING_REQUIRED_BLOCK` | 집합마다 그중 하나 이상이 배치돼 있어야 함(REPEAT 안 포함) |
| `forbiddenBlocks` | `FORBIDDEN_BLOCK` | 하나라도 배치돼 있으면 실패 |
| `requiredOrder` | `ORDER_NOT_SATISFIED` | REPEAT를 펼친 실행 순서에 부분 수열로 나타나야 함 |
| `minimumExecutions` | `TOO_FEW_EXECUTIONS` | REPEAT를 펼친 실행 횟수 |
| `lastAction` | `LAST_ACTION_MISMATCH` | 마지막 동작(START·END·WAIT 제외) |
| `maxBlockCount` | `TOO_MANY_BLOCKS` | 배치된 블록 수(START·END 제외, REPEAT와 그 안 블록 각각 1개) |

`detail`은 사람이 읽는 보조 값(블록 타입 이름, `A|B` 대안, `x4` 횟수 등)이다. Client 문구는 `code` 기준으로 만든다.

## 미션별 규칙 (Notion "난이도별 체험 미션 9종" 완료 조건 중 블록 구성으로 볼 수 있는 것)

| # | 미션 | 규칙 | 판정하지 않는 조건 |
| --- | --- | --- | --- |
| 1 | 로봇 택배 배달 | 전진 포함, 마지막 동작 정지 | 이동 거리 범위 |
| 2 | 뒤를 봐! | 회전 포함, 전진·후진 금지, 마지막 동작 정지 | 목표 회전각 오차 |
| 3 | 인사하고 출발! | 앉기 → 일어서기 → 전진 순서, 마지막 동작 정지 | — |
| 4 | 편의점 심부름 | 전진 → 회전 → 전진 순서 | 도착 영역·방향 오차 |
| 5 | 무대 중앙에서 포즈 | 전진 → 정지 → 프리셋 순서 | 목표 지점 도착 (허용 프리셋은 블록 검증 #117이 이미 강제) |
| 6 | 집으로 돌아가기 | 전진 → 회전 → 전진 순서 | 시작 지점 반경, 실행 시간 |
| 7 | 사각 순찰대 | 반복 블록 포함, 전진 4회·회전 4회 이상 실행 | 시작점 복귀, 전체 회전량 |
| 8 | 숫자 8 드라이브 | 좌회전·우회전 모두 포함 | 루프 모양, 거리·시간, 코스 이탈 |
| 9 | 최소 블록 챌린지 | 없음(항상 통과) | 최대 블록 수(숫자 미정), 목표 위치·방향 |

3번의 "일어서기 완료 후에만 이동 시작"은 부분 순서(앉기 → 일어서기 → 전진)로만 확인한다. 앉기 전에 전진 블록이 하나 더 있어도 통과한다.

9번은 Notion에 최대 블록 수 숫자가 없어 규칙을 비워 뒀다. 숫자가 정해지면 `maxBlockCount`만 채우면 된다.

`MissionCatalogTest`는 규칙이 참조하는 블록이 모두 그 미션의 `allowedBlocks` 안에 있는지, 그리고 미션마다 `allowedBlocks`만으로 규칙을 통과하는 예시가 있는지 검사한다.
