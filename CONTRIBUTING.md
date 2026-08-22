# CONTRIBUTING

POPPY 서버 저장소에 기여하는 방법을 설명한다. AI 하네스 규칙은 `CLAUDE.md`/`AGENTS.md`를 따르고, 이 문서는 사람과 AI 모두에게 적용되는 작업 절차를 다룬다.

## 작업 절차

1. 작업 범위에 해당하는 GitHub Issue를 생성하거나 확인한다.
2. `develop`을 최신 상태로 가져온다.
3. `docs/git-workflow.md`의 브랜치 명명 규칙에 따라 `develop`에서 작업 브랜치를 생성한다.
4. Issue 범위 안에서만 구현한다.
5. 관련 테스트와 전체 테스트, 전체 빌드를 실행한다.
6. `docs/commit-convention.md`에 따라 커밋한다.
7. 원격 브랜치로 push하고 `develop` 대상 PR을 생성한다.
8. PR 본문에 `Closes #이슈번호`로 Issue를 연결한다.
9. CI가 모두 성공하면 Squash Merge한다.
10. merge 후 원격 작업 브랜치를 삭제한다.

## 브랜치와 커밋

- 브랜치 전략은 `docs/git-workflow.md` 참고
- 커밋 메시지 규칙은 `docs/commit-convention.md` 참고
- `main`과 `develop`에는 직접 commit/push하지 않는다.

## 테스트

- 변경된 동작에는 테스트를 추가하거나 수정한다.
- 테스트를 삭제하거나 비활성화하지 않는다.
- 전체 테스트와 빌드를 통과한 뒤에만 PR을 생성한다.
- 자세한 내용은 `docs/testing.md` 참고

## 문서

- 아키텍처 규칙: `docs/architecture.md`
- API 규칙: `docs/api-convention.md`
- 로컬 환경/설정: `docs/configuration.md`
- AI 작업 규칙: `docs/ai-workflow.md`
- CI/알림: `docs/ci.md`

## 보안

비밀값 관련 규칙은 `SECURITY.md`를 따른다.
