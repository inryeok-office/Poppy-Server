# 커밋 메시지 규칙

Conventional Commits 형식을 사용한다.

```text
type(scope): subject
```

## 허용 type

- `feat`
- `fix`
- `refactor`
- `test`
- `docs`
- `chore`
- `ci`
- `build`
- `style`

## 규칙

- type과 scope는 영어 소문자
- subject는 한글 사용 가능
- subject 끝에 마침표 금지
- 명령형 또는 변경 내용 중심으로 작성
- 72자를 크게 넘기지 않는다.
- 하나의 커밋에는 하나의 논리적 목적만 포함한다.
- `temp`, `update`, `수정`, `작업`처럼 의미가 불분명한 메시지 금지
- 자동 생성 도구 이름을 커밋 메시지에 넣지 않는다.
- `Co-Authored-By`를 임의로 추가하지 않는다.

## 예시

- `chore(harness): AI 작업 규칙을 추가한다`
- `refactor(architecture): global 및 domain 구조를 적용한다`
- `chore(database): PostgreSQL 개발 환경을 구성한다`
- `feat(global): 공통 예외 처리를 추가한다`
- `ci(github): Gradle 검증 워크플로를 추가한다`
- `ci(discord): CI 결과 Embed 알림을 추가한다`
- `docs(readme): 로컬 실행 방법을 작성한다`

## PR Squash Merge

PR을 Squash Merge할 때 최종 커밋 메시지는 PR의 목적을 나타내도록 정리한다.
