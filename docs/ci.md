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
