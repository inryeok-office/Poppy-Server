# 관리자 인증 API

관리자 인증은 세션 쿠키 방식이다. 계정은 환경변수(`POPPY_ADMIN_USERNAME`, `POPPY_ADMIN_PASSWORD_HASH`)로 주입하며 둘 중 하나라도 비어 있으면 어떤 로그인도 실패한다. 설정 방법은 `docs/configuration.md`를 따른다.

## POST /api/v1/admin/auth/login

요청 body:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `username` | string | 필수, 공백 불가 |
| `password` | string | 필수, 공백 불가 |

성공 시 `200`과 함께 `{ "success": true, "data": { "authenticated": true }, "error": null }`을 반환하고 `Set-Cookie`로 관리자 세션 쿠키를 발급한다.

| 상태 | 코드 | 설명 |
| --- | --- | --- |
| 400 | `COMMON_400` | username 또는 password가 비어 있음 |
| 401 | `ADMIN_CREDENTIAL_INVALID` | username과 password 중 무엇이 틀렸는지 구분하지 않음 |
| 429 | `ADMIN_LOGIN_RATE_LIMITED` | `remoteAddr + username` 기준 윈도우당 시도 횟수 초과, credential 검증 전에 판정 |
| 500 | `ADMIN_LOGIN_FAILED` | 로그인 처리 중 예상하지 못한 오류 |

로그인 성공 시 해당 키의 시도 기록은 초기화된다.

## POST /api/v1/admin/auth/logout

현재 쿠키의 세션을 무효화하고 `204`를 반환하며 `Max-Age=0` 쿠키로 삭제를 지시한다.

| 상태 | 코드 | 설명 |
| --- | --- | --- |
| 401 | `ADMIN_SESSION_INVALID` | 쿠키 없음, 위조, 만료, 이미 무효화됨 |
| 500 | `ADMIN_LOGOUT_FAILED` | 로그아웃 처리 중 예상하지 못한 오류 |

## 세션 쿠키

| 속성 | 값 |
| --- | --- |
| 이름 | `POPPY_ADMIN_SESSION` |
| `HttpOnly` | 항상 |
| `Path` | `/api/v1/admin` |
| `Max-Age` | `POPPY_ADMIN_SESSION_TTL`(기본 `PT8H`), 로그인 시점 기준 고정 만료 |
| `SameSite` | `POPPY_ADMIN_COOKIE_SAME_SITE`(기본 `Strict`) |
| `Secure` | `POPPY_ADMIN_COOKIE_SECURE`(기본 `true`, `local` 프로필은 `false`) |

토큰은 `SecureRandom` 32바이트를 base64url로 인코딩한 값이며 DB(`admin_sessions`)에는 SHA-256 다이제스트만 저장한다. 유효 조건은 `revoked_at IS NULL AND expires_at > now`이고, 만료·무효화된 행은 주기적으로 삭제한다.

## 인증 boundary

`AdminAuthenticationInterceptor`가 `/api/v1/admin/**` 전체에 적용되며 `/api/v1/admin/auth/login`만 제외된다. 쿠키가 없거나 유효한 세션이 아니면 `401 ADMIN_SESSION_INVALID`로 응답한다. 인증에 성공하면 세션 ID가 request attribute `poppy.admin.session-id`에 저장되어 후속 Controller가 사용한다.

CSRF 보호는 비활성화되어 있으므로 상태 변경 요청의 CSRF 방어는 `SameSite` 쿠키 속성에 의존한다.
