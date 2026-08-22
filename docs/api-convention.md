# API 응답 규칙

## 공통 응답 포맷

모든 API 응답은 `ApiResponse<T>`로 감싼다.

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

실패 시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON_400",
    "message": "요청 값이 올바르지 않습니다",
    "fieldErrors": [
      { "field": "name", "reason": "must not be blank" }
    ]
  }
}
```

## 에러 코드

`ErrorCode`는 HTTP 상태, 코드, 기본 메시지를 함께 정의한다. 도메인별 에러 코드가 필요해지면 해당 도메인 구현 시점에 `ErrorCode`를 확장한다.

| 코드 | 상태 | 설명 |
|---|---|---|
| `COMMON_400` | 400 | 요청 값이 올바르지 않음 |
| `COMMON_500` | 500 | 서버 내부 오류 |

## 전역 예외 처리

`GlobalExceptionHandler`(`@RestControllerAdvice`)가 다음을 공통 포맷으로 변환한다.

- `ApplicationException`: 도메인에서 명시적으로 던지는 예외, `ErrorCode`에 정의된 상태로 응답
- `MethodArgumentNotValidException`: Bean Validation 실패, 필드별 오류를 `fieldErrors`에 포함해 400으로 응답
- 그 외 처리되지 않은 예외: 500 `COMMON_500`으로 응답

애플리케이션/도메인 코드에서 새로운 예외가 필요하면 `ApplicationException(errorCode)`를 사용하고, HTTP 상태와 메시지를 직접 다루지 않는다.

## Request/Response DTO

Controller는 JPA Entity를 직접 반환하지 않는다. Request/Response 전용 DTO를 두고, 도메인 모델과 API 계약을 분리한다.

## API 문서

springdoc-openapi가 `/v3/api-docs`, `/swagger-ui.html`을 통해 API 문서를 제공한다. 실제 도메인 API가 추가되면 해당 Controller에 문서가 자동 반영된다.

## Health Check

`/actuator/health`로 애플리케이션 상태를 확인할 수 있다. 기본적으로 `health` 엔드포인트만 노출한다.
