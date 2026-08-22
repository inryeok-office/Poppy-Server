# POPPY 서버 AI 작업 하네스

이 문서는 Claude Code와 Codex가 이 저장소에서 동일한 규칙으로 동작하도록 정의한다.

`CLAUDE.md`와 `AGENTS.md`는 byte 단위로 완전히 동일해야 한다. 한쪽만 수정하는 것을 금지한다. 두 파일이 다르면 CI가 실패한다.

## 프로젝트 개요

POPPY는 일반인·어린이·학생이 블록 코딩으로 Unitree GO2 계열 로봇의 동작을 구성하고, 시뮬레이션을 통과한 뒤 실제 로봇에서 실행하는 행사장 체험 서비스다. 이 저장소는 Spring(Kotlin) 서버만 담당한다. 웹 클라이언트, Python Robot Agent, Unitree SDK2 연동은 이 저장소의 범위가 아니다.

## 문서 우선순위

1. 현재 사용자의 명시적 지시
2. 승인된 GitHub Issue
3. `CLAUDE.md`와 `AGENTS.md`
4. 저장소의 공식 문서 (`docs/`, `README.md`, `CONTRIBUTING.md`, `SECURITY.md`)
5. 기존 코드 관례

상위 규칙과 하위 규칙이 충돌하면 상위 규칙을 따른다. 충돌로 인해 명세나 계약이 바뀌는 경우에는 자동으로 판단하지 말고 중단한다.

## 작업 시작 조건

다음 조건을 모두 확인해야 한다. 하나라도 충족하지 않으면 코드를 변경하지 않는다.

- 현재 작업에 GitHub Issue가 존재한다.
- Issue의 완료 조건을 읽었다.
- 현재 브랜치가 올바른 작업 브랜치다.
- Git status를 확인했다.
- 관련 문서를 읽었다.
- 영향 범위를 파악했다.
- 기존 테스트를 확인했다.

## 범위 제한

- Issue에 없는 기능 추가 금지
- 임의 리팩터링 금지
- 관련 없는 파일 포맷팅 금지
- 관련 없는 의존성 변경 금지
- 기존 API 계약 임의 변경 금지
- 실제 도메인 요구사항 추측 금지
- 미래 기능을 위한 과도한 추상화 금지
- 사용하지 않는 인터페이스와 계층 생성 금지
- 공통화 목적의 무분별한 global 이동 금지
- 사용자 변경사항 되돌리기 금지

## 아키텍처

최상위 소스 구조는 `global`과 `domain`을 기준으로 한다.

```text
src/main/kotlin/{base-package}
├── global
└── domain
```

`global`은 특정 도메인에 속하지 않는 공통 기술 요소만 가진다. 필요하지 않은 패키지는 형식적으로 만들지 않는다.

`domain` 아래에는 기능 단위 패키지(session, mission, simulation, execution, robot, admin, agent)를 두고, 각 도메인은 필요할 때만 `presentation`, `application`, `model`, `infrastructure` 내부 구조를 사용한다.

의존 방향:

- presentation → application
- application → model
- infrastructure → application 또는 model
- model → 외부 계층(Spring Web, JPA, 외부 Client) 의존 금지
- global → domain 의존 금지
- Controller → Repository 직접 호출 금지
- Controller → JPA Entity 직접 반환 금지
- 다른 도메인의 infrastructure 직접 참조 금지
- Request/Response DTO는 도메인 모델과 분리
- 트랜잭션 경계는 application 계층에 둔다.

프로젝트 규모가 작으므로 모든 도메인에 인터페이스와 계층을 무조건 만들지 않는다. 실제 구현이 없는 빈 Controller, Service, Repository, Entity, DTO를 생성하지 않는다.

## 코드 규칙

- global/domain 최상위 구조를 지킨다.
- 도메인 모델은 infrastructure에 의존하지 않는다.
- Controller에서 Repository를 직접 호출하지 않는다.
- Entity를 API 응답으로 직접 반환하지 않는다.
- 요청과 응답 DTO를 분리한다.
- 트랜잭션 경계를 application 계층에 둔다.
- 설정값을 코드에 하드코딩하지 않는다.
- 비밀값을 저장소에 작성하지 않는다.
- 코드에 주석을 작성하지 않는다.
- 의미 있는 이름과 작은 메서드를 사용한다.
- 필요하지 않은 디자인 패턴을 적용하지 않는다.
- 현재 프로젝트의 언어(Kotlin)와 코드 스타일을 따른다.

## 테스트 규칙

- 변경된 동작에 대한 테스트를 작성한다.
- 버그 수정은 재현 테스트를 먼저 작성한다.
- 테스트 삭제 금지
- 테스트 비활성화 금지
- 실패 테스트 무시 금지
- 검증 우회 금지
- 전체 빌드 성공 전 완료 보고 금지
- Testcontainers가 필요한 테스트를 임의로 H2 테스트로 대체하지 않는다.
- 테스트하지 못한 부분은 성공한 것처럼 표현하지 않는다.

## Git Flow

브랜치:

- `main`: 배포 가능한 안정 상태
- `develop`: 다음 배포 통합
- `feature/{issue-number}-{short-name}`: 기능
- `fix/{issue-number}-{short-name}`: 버그 수정
- `refactor/{issue-number}-{short-name}`: 구조 개선
- `chore/{issue-number}-{short-name}`: 설정 및 환경
- `docs/{issue-number}-{short-name}`: 문서
- `hotfix/{issue-number}-{short-name}`: main 긴급 수정
- `release/{version}`: develop에서 main으로 배포 준비

브랜치 이름은 소문자 kebab-case를 사용한다. 자세한 내용은 `docs/git-workflow.md`를 따른다.

## Git 규칙

- main과 develop 직접 commit 금지
- main과 develop 직접 push 금지
- Issue 없는 브랜치 생성 금지
- 강제 push 금지
- 사용자 브랜치 삭제 금지
- CI 실패 상태 merge 금지
- main 대상 PR 자동 merge 금지
- commit 전에 diff와 staged 파일 확인
- push 전에 테스트와 빌드 실행
- PR 생성 후 CI 확인

## GitHub 협업 자동화

- PR 생성 시 작성자를 Assignee로 지정한다(자동화가 처리하지만, 직접 확인한다).
- Reviewer는 `s26059-maker`, `bongbonggoo`, `hej090224` 중 작성자를 제외한 인원을 요청한다.
- 저장소에서 승인된 커스텀 라벨(`.github/labels.json`)만 사용한다. 임의로 새 라벨을 만들지 않는다.
- 브랜치와 변경 파일에 맞는 라벨을 사용한다(`docs/pull-request-convention.md` 참고).
- develop 대상 PR 본문에 연결 Issue를 `Closes #{이슈번호}` 형식으로 작성한다.
- 하나의 PR에서 관련 없는 Issue를 종료하지 않는다.
- main 대상 PR을 자동으로 병합하지 않는다.
- 자동화 실패(Reviewer 요청 실패, 라벨 적용 실패 등)를 무시하거나 수동으로 성공했다고 보고하지 않는다. Job Summary와 실제 상태를 확인한다.
- PR 메타데이터 자동화(Assignee/Reviewer/라벨)가 실제로 적용됐는지 merge 전에 확인한다.
- Claude와 Codex는 이 섹션의 GitHub 작업 규칙을 동일하게 따른다.

## 커밋 규칙

Conventional Commits 형식(`type(scope): subject`)을 사용한다. 자세한 내용은 `docs/commit-convention.md`를 따른다.

- type과 scope는 영어 소문자
- subject는 한글 사용 가능, 끝에 마침표 금지
- 72자를 크게 넘기지 않는다.
- 하나의 커밋에는 하나의 논리적 목적만 포함한다.
- `temp`, `update`, `수정`, `작업`처럼 의미가 불분명한 메시지 금지
- 자동 생성 도구 이름을 커밋 메시지에 넣지 않는다.
- `Co-Authored-By`를 임의로 추가하지 않는다.

## 안전 규칙

- `.env`, 비밀번호, API Key, 인증 토큰 커밋 금지
- Discord Webhook URL 커밋, 로그 출력, Issue/PR/README 작성 금지
- 로봇 IP, Agent 인증 키, 현장 네트워크 비밀 설정 커밋 금지
- destructive Git 명령 금지
- 기존 사용자 데이터를 삭제하는 마이그레이션 자동 실행 금지
- 권한 보호 규칙 우회 금지
- 실패를 숨기기 위한 설정 완화 금지
- Secret 값을 조회하거나 출력하려 시도하지 않는다.

## 완료 보고

각 Issue 완료 후 다음을 보고한다.

- Issue 번호와 링크
- 브랜치
- PR 번호와 링크
- 변경 파일
- 주요 변경사항
- 실행한 검증 명령
- 테스트 결과
- CI 결과
- merge 결과
- 남은 위험과 후속 작업
