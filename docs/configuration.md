# 설정과 로컬 환경

## 프로필

- 기본(`application.yaml`): 공통 설정만 포함, DB 접속 정보 없음
- `local`(`application-local.yaml`): 로컬 PostgreSQL 접속 정보, 환경변수로 값을 주입
- `prod`(`application-prod.yaml`): 운영 PostgreSQL 접속 정보를 환경변수로 주입, springdoc 비활성화, actuator는 health만 노출

로컬에서 실행할 때는 `SPRING_PROFILES_ACTIVE=local`을, 운영 배포에는 `SPRING_PROFILES_ACTIVE=prod`를 사용한다.

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
| `POPPY_AGENT_HEARTBEAT_TIMEOUT_SECONDS` | Robot heartbeat timeout(초) | `90` |
| `POPPY_AGENT_HEARTBEAT_SCAN_INTERVAL_MILLISECONDS` | stale Robot 검사 주기(밀리초) | `30000` |
| `POPPY_AGENT_HEARTBEAT_OFFLINE_BATCH_SIZE` | stale Robot 일괄 처리 크기 | `100` |
| `POPPY_ADMIN_USERNAME` | 관리자 로그인 username, 비어 있으면 로그인 불가 | 설정 필요 |
| `POPPY_ADMIN_PASSWORD_HASH` | 관리자 비밀번호 BCrypt 해시, 비어 있으면 로그인 불가 | 설정 필요 |
| `POPPY_ADMIN_SESSION_TTL` | 관리자 세션 유효 시간(로그인 시점 기준 고정 만료, ISO-8601 Duration) | `PT8H` |
| `POPPY_ADMIN_SESSION_CLEANUP_INTERVAL_MILLISECONDS` | 만료·무효화된 관리자 세션 정리 주기(밀리초) | `60000` |
| `POPPY_ADMIN_LOGIN_ATTEMPT_WINDOW` | 관리자 로그인 시도 제한 윈도우 | `PT1M` |
| `POPPY_ADMIN_LOGIN_MAX_ATTEMPTS` | 윈도우당 IP+username 기준 허용 로그인 시도 횟수 | `5` |
| `POPPY_ADMIN_LOGIN_IP_MAX_ATTEMPTS` | 윈도우당 IP 단독 기준 허용 로그인 시도 횟수(username 변경 우회 방지) | `20` |
| `POPPY_ADMIN_LOGIN_ATTEMPT_CLEANUP_INTERVAL_MILLISECONDS` | 로그인 시도 기록 정리 주기(밀리초) | `60000` |
| `TRUSTED_PROXIES` | `X-Forwarded-For`/`X-Forwarded-Proto`를 신뢰할 리버스 프록시 IP/대역 정규식. 비어 있으면 어떤 프록시도 신뢰하지 않고 실제 연결 IP만 사용한다 | 빈 값 |
| `ADMIN_ALLOWED_ORIGINS` | `/api/v1/admin/**` CORS를 허용할 origin 목록(콤마 구분). 비어 있으면 모든 cross-origin 요청을 거부한다 | 빈 값 |
| `SESSION_COOKIE_SECURE` | 관리자 세션 쿠키(`POPPY_ADMIN_SESSION`)와 CSRF 토큰용 세션 쿠키(`JSESSIONID`) 둘 다에 적용되는 Secure(`local` 프로필은 `false`) | `true` |
| `SESSION_COOKIE_SAME_SITE` | 위 두 쿠키에 공통 적용되는 SameSite, `Strict`/`Lax`만 허용(대소문자 무시). `None`은 관리자 세션 쿠키 발급이 거부되어 애플리케이션이 기동 실패한다(이 앱은 cross-site 관리자 웹을 지원하지 않는다) | `strict` |

## 로컬 데이터베이스 실행

```bash
docker compose up -d
```

`docker-compose.yml`은 PostgreSQL 16을 기동하고 `.env`(또는 위 기본값)의 접속 정보를 사용한다.

## 마이그레이션

Flyway가 `src/main/resources/db/migration`의 SQL 스크립트를 애플리케이션 시작 시 자동 적용한다. 실제 도메인 스키마는 해당 기능이 구현되는 시점에 마이그레이션 스크립트로 추가한다.

## 통합 테스트

`PostgresConnectionIntegrationTest`는 Testcontainers로 PostgreSQL 컨테이너를 띄우고 연결과 Flyway 마이그레이션 적용을 검증한다. 로컬에서 Docker가 실행 중이어야 하며, CI에서도 동일하게 동작한다.

## 관리자 인증 설정

관리자 계정은 DB가 아니라 환경변수로 주입한다. BCrypt 해시는 `htpasswd -bnBC 10 "" '<비밀번호>' | tr -d ':\n'`으로 생성한다(`$2y$` 접두사도 그대로 사용 가능). `.env` 값에는 `$`가 들어가므로 작은따옴표로 감싸고, `docker-compose.yml`에 직접 쓸 때는 `$$`로 이스케이프한다.

`local` 프로필에는 개발용 계정(`admin`)이 정의되어 있으며 운영 환경에서는 반드시 환경변수로 덮어쓴다.
