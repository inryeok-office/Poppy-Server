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
./gradlew test
./gradlew build
```

Windows에서는 `./scripts/harness-check.ps1`을 사용한다.
