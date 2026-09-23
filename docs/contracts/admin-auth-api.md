# 관리자 인증 API

관리자 인증은 세션 쿠키 방식이다. 계정은 환경변수(`POPPY_ADMIN_USERNAME`, `POPPY_ADMIN_PASSWORD_HASH`)로 주입하며 둘 중 하나라도 비어 있으면 어떤 로그인도 실패한다. 설정 방법은 `docs/configuration.md`를 따른다.

## POST /api/v1/admin/auth/login

요청 body:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `username` | string | 필수, 공백 불가, 최대 64자 |
| `password` | string | 필수, 공백 불가, 최대 128자 |

성공 시 `200`과 함께 `{ "success": true, "data": { "authenticated": true }, "error": null }`을 반환하고 `Set-Cookie`로 관리자 세션 쿠키를 발급한다.

| 상태 | 코드 | 설명 |
| --- | --- | --- |
| 400 | `COMMON_400` | username 또는 password가 비어 있거나 길이 제한(username 64자, password 128자) 초과 |
| 401 | `ADMIN_CREDENTIAL_INVALID` | username과 password 중 무엇이 틀렸는지 구분하지 않음 |
| 429 | `ADMIN_LOGIN_RATE_LIMITED` | `remoteAddr` 단독(`POPPY_ADMIN_LOGIN_IP_MAX_ATTEMPTS`, 기본 20) 또는 `remoteAddr + username`(`POPPY_ADMIN_LOGIN_MAX_ATTEMPTS`, 기본 5) 기준 윈도우당 시도 횟수 초과, 두 제한 모두 credential 검증(BCrypt) 전에 판정 |
| 500 | `ADMIN_LOGIN_FAILED` | 로그인 처리 중 예상하지 못한 오류 |

로그인 성공 시 `remoteAddr + username` 키의 시도 기록만 초기화된다. `remoteAddr` 단독 카운터는 윈도우 만료로만 초기화된다.

## POST /api/v1/admin/auth/logout

현재 쿠키의 세션을 무효화하고 `204`를 반환하며 `Max-Age=0` 쿠키로 삭제를 지시한다.

| 상태 | 코드 | 설명 |
| --- | --- | --- |
| 401 | `ADMIN_SESSION_INVALID` | 쿠키 없음, 위조, 만료, 이미 무효화됨 |
| 500 | `ADMIN_LOGOUT_FAILED` | 로그아웃 처리 중 예상하지 못한 오류 |

## GET /api/v1/admin/auth/csrf

로그인된 세션에서 CSRF 토큰을 발급받는다. 인증 boundary에 포함되므로 유효한 `POPPY_ADMIN_SESSION` 쿠키가 필요하다.

성공 시 `200`과 함께 다음 형식을 반환한다.

```json
{
  "success": true,
  "data": { "headerName": "X-CSRF-TOKEN", "token": "..." },
  "error": null
}
```

| 상태 | 코드 | 설명 |
| --- | --- | --- |
| 401 | `ADMIN_SESSION_INVALID` | 쿠키 없음, 위조, 만료, 이미 무효화됨 |

## 쿠키

클라이언트는 다음 두 쿠키를 모두 유지해야 한다. 브라우저가 두 쿠키를 함께 자동 전송하므로 별도 저장·재전송 로직은 필요 없다.

| 속성 | `POPPY_ADMIN_SESSION`(관리자 인증) | `JSESSIONID`(CSRF 토큰 저장용 HTTP 세션) |
| --- | --- | --- |
| `HttpOnly` | 항상 | 항상 |
| `Path` | `/api/v1/admin` | 컨텍스트 경로 전체 |
| `Max-Age` | `POPPY_ADMIN_SESSION_TTL`(기본 `PT8H`), 로그인 시점 기준 고정 만료 | 컨테이너 세션 타임아웃 |
| `SameSite` | `SESSION_COOKIE_SAME_SITE`(기본 `strict`, `Strict`/`Lax`만 허용) | `SESSION_COOKIE_SAME_SITE`(동일 값) |
| `Secure` | `SESSION_COOKIE_SECURE`(기본 `true`, `local` 프로필은 `false`) | `SESSION_COOKIE_SECURE`(동일 값) |

두 쿠키는 같은 `SESSION_COOKIE_SECURE`/`SESSION_COOKIE_SAME_SITE` 환경변수를 따른다. `POPPY_ADMIN_SESSION` 토큰은 `SecureRandom` 32바이트를 base64url로 인코딩한 값이며 DB(`admin_sessions`)에는 SHA-256 다이제스트만 저장한다. 유효 조건은 `revoked_at IS NULL AND expires_at > now`이고, 만료·무효화된 행은 주기적으로 삭제한다.

## 인증 boundary

`AdminAuthenticationInterceptor`가 `/api/v1/admin/**` 전체에 적용되며 `/api/v1/admin/auth/login`만 제외된다. 쿠키가 없거나 유효한 세션이 아니면 `401 ADMIN_SESSION_INVALID`로 응답한다. 인증에 성공하면 세션 ID가 request attribute `poppy.admin.session-id`에 저장되어 후속 Controller가 사용한다.

## CSRF

`/api/v1/admin/**`의 상태 변경 요청(POST/PUT/PATCH/DELETE)에는 CSRF 토큰이 필요하다. 로그인(`POST /api/v1/admin/auth/login`)은 예외로 CSRF 토큰 없이 호출할 수 있다.

흐름:

1. 로그인해 `POPPY_ADMIN_SESSION` 쿠키를 발급받는다.
2. `GET /api/v1/admin/auth/csrf`를 호출해 `headerName`과 `token`을 받는다. 이때 서버가 함께 내려주는 `JSESSIONID` 쿠키를 유지해야 하며, 이 쿠키가 없으면 이후 검증이 실패한다.
3. 이후 상태 변경 요청에 `headerName`(기본 `X-CSRF-TOKEN`) 헤더로 `token` 값을 담아 전송하고, `POPPY_ADMIN_SESSION`과 `JSESSIONID` 쿠키를 함께 전송한다.

CSRF 토큰이 없거나 값이 틀리면 `403`으로 응답한다.
