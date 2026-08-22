# 아키텍처

POPPY 서버는 `global`과 `domain`을 최상위 기준으로 하는 기능 중심 클린 아키텍처를 사용한다.

```text
src/main/kotlin/team/inreok/poppyserver
├── PoppyServerApplication.kt
├── global
│   ├── config
│   ├── security
│   ├── error
│   ├── response
│   ├── event
│   └── util
└── domain
    ├── session
    ├── mission
    ├── simulation
    ├── execution
    ├── robot
    ├── admin
    └── agent
```

## global

특정 비즈니스 도메인에 속하지 않는 공통 기술 요소만 둔다. 필요하지 않은 하위 패키지는 형식적으로 만들지 않는다. 공통이라는 이유만으로 비즈니스 로직을 global로 이동하지 않는다. `global`은 개별 `domain`을 참조하지 않는다.

## domain

기능 단위 패키지를 둔다. 각 도메인은 필요할 때 다음 내부 구조를 사용한다.

```text
domain/{domain-name}
├── presentation
├── application
├── model
└── infrastructure
```

| 계층 | 역할 |
|---|---|
| presentation | Controller, Request/Response DTO, HTTP 변환, 입력 검증 |
| application | Use Case, Application Service, Port, 트랜잭션 경계, 도메인 작업 조정 |
| model | Entity, Value Object, Enum, 도메인 정책, 도메인 이벤트 |
| infrastructure | JPA Repository 구현, Persistence Adapter, 외부 API Client, Robot Agent Client |

## 의존 규칙

- `presentation` → `application`
- `application` → `model`
- `infrastructure` → `application` 또는 `model`
- `model` → Spring Web, JPA Repository, 외부 Client 의존 금지
- `global` → `domain` 의존 금지
- Controller → Repository 직접 호출 금지
- Controller → JPA Entity 직접 반환 금지
- 다른 도메인의 `infrastructure` 직접 참조 금지
- API DTO와 도메인 모델 분리
- 트랜잭션은 `application` 계층에서 관리
- 도메인 간 연동은 ID, Port, 이벤트 등 명시적 계약을 사용하고 다른 도메인의 Entity를 직접 참조하지 않는다.

## 현재 적용 범위

이번 초기세팅에서는 실제 Poppy 도메인 기능을 구현하지 않는다. `domain` 하위 패키지는 실제 기능이 구현되는 시점에 생성하며, 빈 Controller/Service/Repository/Entity/DTO를 미리 만들지 않는다. `global`도 실제 공통 기반 코드(예: 공통 예외 처리, 공통 API 응답)가 필요한 시점에 채워진다.

## 검증

`src/test/kotlin/team/inreok/poppyserver/architecture/ArchitectureTest.kt`의 ArchUnit 테스트가 다음을 검증한다.

- `global` 패키지의 클래스는 `domain` 패키지에 의존하지 않는다.
- `domain.{name}.presentation` → `domain.{name}.application` → `domain.{name}.model` 계층 의존 방향을 벗어나지 않는다.

새 도메인 패키지가 추가될 때마다 이 테스트가 실제 코드에 대해 규칙을 검증한다.
