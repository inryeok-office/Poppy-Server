# Poppy-Server

POPPY는 일반인·어린이·학생이 블록 코딩으로 Unitree GO2 계열 로봇의 동작을 구성하고, 시뮬레이션을 통과한 뒤 실제 로봇에서 실행하는 행사장 체험 서비스다. 이 저장소는 Spring(Kotlin) 서버를 담당하며, 웹 클라이언트·Python Robot Agent·Unitree SDK2 연동은 별도 저장소 범위다.

## 기술 스택

- Kotlin, Spring Boot 4.1.1
- Java 25 (Toolchain)
- Gradle Kotlin DSL (Wrapper)
- Spring Web MVC, Spring Data JPA, Spring Validation, Spring Security
- PostgreSQL, Flyway
- springdoc-openapi, Spring Boot Actuator
- JUnit 5, ArchUnit, Testcontainers

## 로컬 실행

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

환경변수는 `.env.example`을 참고한다. 자세한 내용은 [`docs/configuration.md`](docs/configuration.md)를 참고한다.

## 테스트

```bash
./gradlew test
./gradlew build
```

Testcontainers 기반 통합 테스트가 포함되어 있어 로컬 Docker가 필요하다. 자세한 내용은 [`docs/testing.md`](docs/testing.md)를 참고한다.

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/architecture.md`](docs/architecture.md) | global/domain 클린 아키텍처와 의존 규칙 |
| [`docs/git-workflow.md`](docs/git-workflow.md) | Git Flow 브랜치/머지 규칙 |
| [`docs/commit-convention.md`](docs/commit-convention.md) | 커밋 메시지 규칙 |
| [`docs/api-convention.md`](docs/api-convention.md) | 공통 응답/에러 포맷 |
| [`docs/configuration.md`](docs/configuration.md) | 프로필, 환경변수, 로컬 DB 실행 |
| [`docs/testing.md`](docs/testing.md) | 테스트 종류와 실행 방법 |
| [`docs/ci.md`](docs/ci.md) | CI 구성과 Discord 알림 |
| [`docs/ai-workflow.md`](docs/ai-workflow.md) | Claude Code/Codex 공통 작업 방식 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 기여 절차 |
| [`SECURITY.md`](SECURITY.md) | 비밀값/보안 규칙 |
| [`CLAUDE.md`](CLAUDE.md) / [`AGENTS.md`](AGENTS.md) | AI 하네스 (byte 단위 동일) |
