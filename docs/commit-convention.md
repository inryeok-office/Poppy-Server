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
- subject는 완전한 문장보다 변경 작업을 요약하는 명사형 표현을 기본으로 한다.
- 변경 대상과 목적이 드러나는 간결한 작업명 중심으로 작성한다.
- 기본적으로 `~한다`, `~했다`, `~합니다` 같은 문장형 종결을 사용하지 않는다.
- 모든 문장을 억지로 명사형으로 바꾸지 않으며, subject의 의미 전달과 가독성을 우선한다.
- 72자를 크게 넘기지 않는다.
- 하나의 커밋에는 하나의 논리적 목적만 포함한다.
- `temp`, `update`, `수정`, `작업`처럼 의미가 불분명한 메시지 금지
- 자동 생성 도구 이름을 커밋 메시지에 넣지 않는다.
- `Co-Authored-By`를 임의로 추가하지 않는다.

## 예시

- `feat(robot): Robot 관리 API 구현`
- `feat(agent): Agent 등록 및 Heartbeat 처리 추가`
- `fix(robot): 중복 Agent 연결 검증 수정`
- `refactor(execution): 실행 상태 전이 로직 분리`
- `test(robot): Robot persistence 통합 테스트 추가`
- `docs(harness): GitHub 작성 문체 규칙 정리`
- `ci(github): PR 메타데이터 검증 강화`

## PR Squash Merge

PR을 Squash Merge할 때 최종 커밋 메시지는 PR의 목적을 나타내도록 정리한다.
