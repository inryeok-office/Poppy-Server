# 설정과 로컬 환경

## 프로필

- 기본(`application.yaml`): 공통 설정만 포함, DB 접속 정보 없음
- `local`(`application-local.yaml`): 로컬 PostgreSQL 접속 정보, 환경변수로 값을 주입

로컬에서 실행할 때는 `SPRING_PROFILES_ACTIVE=local`을 사용한다.

## 환경변수

`.env.example`을 복사해 `.env`로 만들고 값을 채운다. `.env`는 `.gitignore`에 포함되어 커밋되지 않는다.

| 변수 | 설명 | 기본값 |
|---|---|---|
| `DB_HOST` | PostgreSQL 호스트 | `localhost` |
| `DB_PORT` | PostgreSQL 포트 | `5432` |
| `DB_NAME` | 데이터베이스 이름 | `poppy` |
| `DB_USERNAME` | 데이터베이스 사용자 | `poppy` |
| `DB_PASSWORD` | 데이터베이스 비밀번호 | `poppy` |
| `POPPY_AGENT_TOKEN` | 내부 Robot Agent API 인증 토큰 | 설정 필요 |

## 로컬 데이터베이스 실행

```bash
docker compose up -d
```

`docker-compose.yml`은 PostgreSQL 16을 기동하고 `.env`(또는 위 기본값)의 접속 정보를 사용한다.

## 마이그레이션

Flyway가 `src/main/resources/db/migration`의 SQL 스크립트를 애플리케이션 시작 시 자동 적용한다. 실제 도메인 스키마는 해당 기능이 구현되는 시점에 마이그레이션 스크립트로 추가한다.

## 통합 테스트

`PostgresConnectionIntegrationTest`는 Testcontainers로 PostgreSQL 컨테이너를 띄우고 연결과 Flyway 마이그레이션 적용을 검증한다. 로컬에서 Docker가 실행 중이어야 하며, CI에서도 동일하게 동작한다.
