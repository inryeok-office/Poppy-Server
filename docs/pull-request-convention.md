# PR 자동화 규칙

develop 대상 PR에는 다음이 자동으로 적용된다(`.github/workflows/pr-metadata.yml`, `.github/scripts/pr-metadata.js`, `.github/scripts/pr-rules.js`). Metadata는 열려 있는 PR에서만 수행하며, merged/closed PR의 title/body 편집은 재적용 대상이 아니다.

## Assignee

PR 작성자를 Assignee로 지정한다. 이미 지정되어 있으면 다시 지정하지 않는다. Bot이 생성한 PR은 Bot을 Assignee로 지정하지 않는다.

## Reviewer

고정된 서버 개발자 3명(`s26059-maker`, `bongbonggoo`, `hej090224`) 중 PR 작성자를 제외한 인원을 Reviewer로 요청한다. 작성자가 3명에 포함되지 않으면 3명 모두 요청한다.

- 이미 요청되었거나 이미 리뷰를 남긴 사용자에게는 다시 요청하지 않는다.
- Bot 작성 PR에는 Reviewer를 요청하지 않는다.
- Draft PR에는 요청하지 않고, Ready for review로 전환되면 요청한다.
- 일부 요청이 실패해도(예: 저장소 접근 권한 없음) 나머지 요청은 그대로 적용되고, 실패 사유는 Job Summary에 남는다.

## 라벨

승인된 커스텀 라벨(`.github/labels.json`)만 사용한다. 브랜치 이름과 변경된 파일 경로를 기준으로 자동 계산되며, 재실행 시 더 이상 조건에 맞지 않는 자동 라벨은 제거된다. 규칙은 `.github/scripts/pr-rules.js`를 기준으로 한다.

- 브랜치 prefix: `feature/`→feature, `fix/`·`hotfix/`→bug, `refactor/`→refactor, `chore/`→chore, `docs/`→documentation, `release/`→release
- `develop` → `main` PR은 `release`
- 경로 기준: `.github/**`→ci, CI 관련 `scripts/**`→ci, `src/test/**`·`*Test.kt`·`*Tests.kt`→test, Gradle 설정 파일→dependencies, `src/main/**/domain/{name}/**`→해당 domain 라벨
- PR 제목의 `!:` 표기 또는 본문의 `BREAKING CHANGE` 문구는 `breaking-change`

## 보안 설계

이 워크플로는 `pull_request_target`을 사용하지만 PR head는 checkout하지 않는다. `actions/checkout`에 `ref: ${{ github.event.pull_request.base.ref }}`를 명시해 실행 시점의 신뢰된 base 브랜치 코드만 checkout하고, PR에서 제출된 코드나 스크립트는 실행하지 않는다. 실제 동작은 GitHub REST API 호출로만 이루어진다.

## 연결 Issue

PR 본문에는 `Closes #{이슈번호}` 형식으로 연결 Issue를 명시한다. 하나의 PR에서 관련 없는 Issue를 종료하지 않는다. develop 대상 PR이 병합되면 연결된 Issue가 자동으로 종료된다(`docs/ci.md`의 이슈 자동 종료 섹션 참고).
