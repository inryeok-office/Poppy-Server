# Git Flow

## 브랜치

| 브랜치 | 용도 |
|---|---|
| `main` | 배포 가능한 안정 상태 |
| `develop` | 다음 배포를 위한 통합 브랜치 |
| `feature/{issue-number}-{short-name}` | 기능 |
| `fix/{issue-number}-{short-name}` | 버그 수정 |
| `refactor/{issue-number}-{short-name}` | 구조 개선 |
| `chore/{issue-number}-{short-name}` | 설정 및 환경 |
| `docs/{issue-number}-{short-name}` | 문서 |
| `hotfix/{issue-number}-{short-name}` | main 긴급 수정 |
| `release/{version}` | develop에서 main으로 배포 준비 |

브랜치 이름은 영어 소문자 kebab-case를 사용한다. 초기세팅 작업은 기본적으로 `chore`, `refactor`, `docs` 중 적절한 유형을 사용한다.

## develop이 없는 경우

저장소에 `main`만 있고 `develop`이 없다면 다음을 수행한다.

1. `main`이 원격과 동일한지, 초기 기준점으로 사용 가능한지 확인한다.
2. 사용자 승인 범위 안에서 `main` 기준 `develop`을 생성한다.
3. `develop`을 원격에 push한다.
4. 이후 모든 작업은 `develop`에서 분기한다.

## Merge 정책

- 작업 PR: `develop` 대상, Squash Merge
- `main` 대상 최종 PR: merge 금지
- 강제 push 금지
- Git history 재작성 금지
- 사용자 브랜치 삭제 금지
- 현재 작업으로 생성한 브랜치만 merge 후 삭제

## 직접 commit/push 금지

`main`과 `develop`에는 직접 commit하거나 push하지 않는다. 모든 변경은 Issue와 PR을 통해 반영한다.
