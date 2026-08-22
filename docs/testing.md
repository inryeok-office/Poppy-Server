# 테스트

## 원칙

- 변경된 동작에는 테스트를 추가하거나 수정한다.
- 버그 수정은 재현 테스트를 먼저 작성한다.
- 테스트를 삭제하거나 비활성화하지 않는다.
- Testcontainers가 필요한 테스트를 임의로 H2로 대체하지 않는다.
- 전체 테스트와 빌드를 통과한 뒤에만 PR을 생성한다.

## 종류

| 종류 | 위치 | 설명 |
|---|---|---|
| 스모크 테스트 | `PoppyServerApplicationTests` | 애플리케이션 컨텍스트 기동 확인 |
| 아키텍처 테스트 | `architecture/ArchitectureTest` | ArchUnit으로 global/domain 의존 규칙 검증 |
| 슬라이스/통합 테스트 | `global/*Test` | 공통 예외 처리, Actuator, OpenAPI 등 검증 |
| 통합 테스트(Testcontainers) | `infrastructure/PostgresConnectionIntegrationTest` | 실제 PostgreSQL 컨테이너 연결과 Flyway 마이그레이션 검증 |

## 실행

```bash
./gradlew test
./gradlew build
```

Testcontainers를 사용하는 테스트는 로컬 Docker(또는 CI의 기본 Docker)가 필요하다.

## DB 미설정 컨텍스트 테스트

DB 접속 정보가 없는 기본 프로필에서 전체 컨텍스트를 검증해야 하는 테스트(`PoppyServerApplicationTests`, `GlobalExceptionHandlerTest`, `GlobalFoundationSmokeTest`)는 `spring.autoconfigure.exclude`로 DataSource/JPA 관련 자동 설정을 테스트 한정으로 제외한다. 실제 DB 연결 자체를 검증해야 하는 테스트는 Testcontainers를 사용한다.
