# CI

## 트리거

`.github/workflows/ci.yml`은 다음 이벤트에서 실행된다.

- `develop` push
- `main` push
- `develop` 대상 PR
- `main` 대상 PR

## 단계

1. Checkout
2. JDK(Temurin 25) 설정
3. Gradle Wrapper 검증
4. 하네스 동일성 검사 (`scripts/harness-check.sh`: CLAUDE.md/AGENTS.md 존재·동일·LF, 필수 문서 존재)
5. 테스트 (`./gradlew test`, ArchUnit·Testcontainers 포함)
6. 빌드 (`./gradlew build`)

## 권한

워크플로 권한은 기본적으로 `contents: read`로 제한한다. PR merge 등 추가 write 권한은 CI 워크플로에 부여하지 않으며, merge는 작업 진행 과정에서 GitHub CLI로 수행한다.

## 로컬 재현

CI와 동일한 검증을 로컬에서 실행할 수 있다.

```bash
bash scripts/harness-check.sh
bash scripts/test-discord-notify.sh
./gradlew test
./gradlew build
```

Windows에서는 `./scripts/harness-check.ps1`을 사용한다.

## Discord 알림

`build-and-test` Job이 끝나면 `notify-discord` Job이 `needs`로 연결되어 항상(`always()`) 실행되며, 최종 성공/실패/취소 결과를 Discord Webhook Embed로 한 번만 전송한다. 알림 Job의 성공 여부가 `build-and-test`의 실제 성공/실패 상태를 바꾸지 않는다.

### Secret

- 이름: `DISCORD_WEBHOOK_URL`
- 등록 위치: 저장소 Settings → Secrets and variables → Actions
- 값은 저장소, 워크플로 파일, 로그, Issue/PR, README 어디에도 작성하지 않는다.

Secret이 등록되지 않은 경우 `scripts/discord-notify.sh`가 전송 단계만 안전하게 건너뛰고, 빌드·테스트 결과에는 영향을 주지 않는다. 로그에는 "DISCORD_WEBHOOK_URL is not set" 문구만 남는다.

### 알림 대상 제한

Fork PR 등 Secret이 제공되지 않는 컨텍스트에서는 알림을 생략하도록 `github.event.pull_request.head.repo.full_name == github.repository` 조건으로 제한한다. `pull_request_target`은 사용하지 않는다.

### 색상

| 상태 | 제목 | 색상 |
|---|---|---|
| 성공 | ✅ CI 성공 | 초록 (`3066993`) |
| 실패 | ❌ CI 실패 | 빨강 (`15158332`) |
| 취소 | ⚠️ CI 취소 | 주황 (`15105570`) |

### Mention

`allowed_mentions.parse`를 빈 배열로 고정해 `@everyone`, `@here`, 역할/사용자 멘션이 전송되지 않는다.

### 테스트

`scripts/test-discord-notify.sh`는 실제 Webhook 전송 없이 성공/실패/취소 payload 생성, JSON 유효성, 필수 필드, allowed mentions 비활성화, 문자열 escape, 길이 제한, Secret 미설정 시 skip 동작을 검증한다. CI의 `Discord payload tests` 단계에서 동일하게 실행된다.

## 라벨 동기화

`.github/labels.json`이 승인된 커스텀 라벨의 단일 기준이다. `.github/workflows/sync-labels.yml`이 `main`/`develop` push 중 `.github/labels.json`이 변경된 경우와 `workflow_dispatch` 수동 실행에서 `.github/scripts/sync-labels.js`를 실행해 저장소 라벨을 이 파일과 동일하게 맞춘다.

- 승인 목록에 없는 이름의 기존 라벨은 삭제된다.
- 승인된 라벨이 없으면 생성되고, 색상/설명이 다르면 갱신된다.
- 동일 상태에서 재실행하면 변경이 발생하지 않는다(멱등성).
- 삭제된 라벨이 사용 중이던 Issue/PR 번호를 Job Summary에 기록한다.
- 워크플로 권한은 `issues: write`, `contents: read`만 사용하며, `pull_request` 이벤트로는 트리거하지 않는다(Fork PR이 라벨 정책 파일을 임의로 실행할 수 없음).

라벨 동기화 로직은 `.github/scripts/labels.js`(순수 계산)와 `.github/scripts/sync-labels.js`(GitHub API 실행)로 분리되어 있으며, `node --test ".github/scripts/tests/*.test.js"`로 로컬/CI에서 동일하게 테스트한다.

## PR 메타데이터 자동화

자세한 규칙은 [`docs/pull-request-convention.md`](pull-request-convention.md)를 참고한다. `.github/workflows/pr-metadata.yml`이 `pull_request_target`(`opened`/`reopened`/`synchronize`/`edited`/`ready_for_review`)에서 PR 작성자 Assignee 지정, Reviewer 자동 요청, 브랜치·경로 기반 라벨 지정을 수행한다.

## 이슈 자동 종료

`.github/workflows/close-linked-issues.yml`이 `pull_request_target`의 `closed` 이벤트에서, PR이 실제로 병합(`merged=true`)되었고 base가 `develop`인 경우에만 본문에서 `Closes #N` 등 키워드로 연결된 같은 저장소 Issue를 종료(completed)하고 코멘트를 남긴다. 단순 종료(닫기만 한 경우), 키워드 없는 `#번호`, 다른 저장소 참조, PR 번호는 종료 대상에서 제외한다. 파싱 로직은 `.github/scripts/linked-issues.js`에 분리되어 있다.

## pull_request_target 보안 설계

`pr-metadata.yml`, `close-linked-issues.yml`은 `pull_request_target`을 사용하지만 `actions/checkout`에 `ref: ${{ github.event.pull_request.base.sha }}`를 명시해 PR head가 아닌 신뢰된 base 브랜치 코드만 checkout한다. PR에서 제출된 코드나 스크립트는 실행하지 않으며, 모든 동작은 GitHub REST API 호출로만 이루어진다. 이 방식을 선택한 이유는 로직을 워크플로 YAML에 전부 인라인으로 넣는 대신 `.github/scripts/*.js`로 분리해 `node --test`로 검증 가능하게 하면서도, base 브랜치는 이미 리뷰·병합을 거친 신뢰된 코드이므로 PR head를 checkout하는 것과 동일한 위험이 없기 때문이다.

## 소급 적용

`.github/workflows/retroactive.yml`(`workflow_dispatch`)이 `.github/scripts/retroactive.js`를 실행해, 이미 병합된 PR 목록에는 Assignee·라벨을 적용하고 연결 Issue를 종료하며(Reviewer는 요청하지 않음), 아직 열려 있는 PR 목록에는 Assignee·Reviewer·라벨을 모두 적용한다(종료·병합은 하지 않음). 입력값의 기본값은 초기세팅 당시 완료된 Issue #1·#3·#5·#7·#9·#11·#13, PR #2·#4·#6·#8·#10·#12·#14, 그리고 열려 있는 PR #15다. 동일 입력으로 재실행해도 중복 코멘트·중복 Reviewer 요청·중복 Assignee가 발생하지 않는다.
