# SECURITY

## 비밀값 관리 원칙

다음 값은 저장소에 절대 커밋하지 않는다.

- `.env` 파일 및 실제 환경변수 값
- 데이터베이스 비밀번호, 접속 문자열
- API Key, 인증 토큰
- `DISCORD_WEBHOOK_URL`
- 로봇 IP, Robot Agent 인증 키, 현장 네트워크 비밀 설정

비밀값은 GitHub Actions Secret 또는 로컬 `.env` 파일(`.gitignore`에 포함됨)로만 관리한다. 저장소에는 `.env.example`처럼 값이 없는 예시 파일만 둔다.

## GitHub Actions Secret

- Secret은 저장소 Settings → Secrets and variables → Actions에서 등록한다.
- CI 워크플로는 Secret의 존재 여부만 확인하며, 값을 로그나 출력물에 노출하지 않는다.
- Secret이 없는 워크플로 단계는 실패시키지 않고 안전하게 건너뛴다.
- `gh secret list`처럼 이름 목록만 조회하는 명령은 허용하되, 값을 조회하거나 출력하는 시도는 하지 않는다.

## 취약점 및 보안 이슈 보고

이 저장소에서 보안 문제를 발견한 경우, Public Issue로 등록하지 말고 저장소 관리자에게 비공개로 알린다.

## AI 하네스와 보안

Claude Code와 Codex는 `CLAUDE.md`/`AGENTS.md`의 안전 규칙을 따른다. 특히 다음을 금지한다.

- 비밀값을 코드나 문서에 하드코딩
- Secret 값을 조회하거나 출력
- branch protection, 필수 리뷰 등 보호 규칙 우회
- 실패를 숨기기 위한 CI 조건 완화
