# Mission 조회 API

`GET /api/v1/missions`와 `GET /api/v1/missions/{missionId}`는 Notion "난이도별 체험 미션 9종" 문서를 정적 catalog로 옮긴 조회 전용 API다. `MissionCatalog`(`domain/mission/application/MissionCatalog.kt`)가 `BlockDefinitionCatalog`(#85)와 동일한 패턴(`@Component` + companion 정적 리스트)으로 고정 데이터를 반환하며, DB나 Flyway 마이그레이션을 사용하지 않는다.

두 endpoint 모두 Session 소유 데이터가 아니므로 `X-Session-Token` 없이 조회할 수 있다.

## GET /api/v1/missions

`ApiResponse` 형식으로 감싼 `data.missions` 배열을 반환한다. 각 항목은 다음 필드를 가진다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `missionId` | UUID | 고정 상수 |
| `title` | string | 미션 제목 |
| `difficulty` | string | `EASY` / `NORMAL` / `HARD` |
| `summary` | string | Notion 원문의 "컨셉" |
| `estimatedSeconds` | number \| null | 항상 `null` (예상 소요 시간 미확정) |

목록 순서는 catalog에 정의된 순서이며, 아래 8개 미션을 이 순서로 포함한다.

1. 로봇 택배 배달 (EASY)
2. 뒤를 봐! (EASY)
3. 인사하고 출발! (EASY)
4. 편의점 심부름 (NORMAL)
5. 집으로 돌아가기 (NORMAL)
6. 사각 순찰대 (HARD)
7. 숫자 8 드라이브 (HARD)
8. 최소 블록 챌린지 (HARD)

## GET /api/v1/missions/{missionId}

`ApiResponse`로 감싼 `data`에 다음 필드를 반환한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `missionId` | UUID | 고정 상수 |
| `title` | string | 미션 제목 |
| `difficulty` | string | `EASY` / `NORMAL` / `HARD` |
| `description` | string | Notion 원문의 "컨셉" (현재 `summary`와 동일한 값) |
| `goal` | string | Notion 원문의 "목표" |
| `completionCondition.description` | string | Notion 원문의 "완료 조건" bullet들을 `\n`으로 연결한 텍스트 |
| `timeLimitSeconds` | number \| null | 항상 `null` (제한 시간 미확정) |
| `allowedBlocks` | string[] | `BlockType` 이름 배열, `START`/`END` 제외 |

`missionId`가 catalog에 없으면 `MISSION_NOT_FOUND`(404)를 반환한다. `missionId` path variable이 UUID 형식이 아닌 경우 전용 처리기가 없어 기존 `GlobalExceptionHandler`의 catch-all 경로로 떨어져 `COMMON_500`으로 응답된다. 이는 이 API만의 문제가 아니라 `sessionId`/`executionId` 등 UUID path variable을 쓰는 기존 endpoint 전체에 공통인 동작이며, 이번 범위에서 새 핸들러를 추가하지 않는다.

## description과 summary가 동일한 이유

Notion 원문에는 미션별로 "컨셉" 텍스트 하나만 있고 목록용 요약과 상세용 설명이 분리되어 있지 않다. 필드를 지어내지 않기 위해 `summary`와 `description`에 같은 "컨셉" 텍스트를 그대로 사용한다.

## PRESET 미션 제외

Notion 원문 5번 미션("무대 중앙에서 포즈")은 `PRESET` 블록이 필요하지만, `BlockDefinitionCatalog`에서 `PRESET`은 `available: false`, `freeModeAllowed: false`로 표시되어 있다(실기기 whitelist 미확정). 이 미션은 이번 catalog에서 제외했으며, 이동/정지 조합으로 대체하지 않았다. PRESET이 실기기 검증을 거쳐 `available: true`가 되면 후속 Issue에서 추가한다.

## 최소 블록 챌린지의 최대 블록 수

9번 미션("최소 블록 챌린지")은 Notion 원문에 "최대 블록 수 이하"라는 완료 조건만 있고 구체적인 숫자가 없다. 별도 필드를 만들지 않고 `completionCondition.description` 텍스트에만 이 조건을 남겨둔다.

## allowedBlocks와 BlockDefinitionCatalog의 관계

`MissionCatalog`의 `allowedBlocks`는 `BlockType` enum 값을 그대로 참조하며, 블록 가용 여부를 다시 하드코딩하지 않는다. `MissionCatalogTest`가 모든 `allowedBlocks` 값이 `BlockDefinitionCatalog`에서 `available=true`인지 검증해 두 catalog 간 계약 불일치를 막는다.
