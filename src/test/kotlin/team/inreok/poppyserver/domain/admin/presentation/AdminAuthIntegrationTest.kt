package team.inreok.poppyserver.domain.admin.presentation

import jakarta.servlet.http.Cookie
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.domain.admin.application.AdminSessionAccessVerifier
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanAdminSessions() {
        jdbcTemplate.update("DELETE FROM admin_sessions")
    }

    @Test
    fun `로그인 성공은 200과 세션 쿠키를 발급하고 DB에는 다이제스트만 저장한다`() {
        val result = login(address = "10.0.1.1")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.authenticated").value(true))
            .andExpect(jsonPath("$.error").value(null))
            .andReturn()

        val setCookie = requireNotNull(result.response.getHeader("Set-Cookie"))
        assertTrue(setCookie.startsWith("POPPY_ADMIN_SESSION="))
        assertTrue(setCookie.contains("HttpOnly"))
        assertTrue(setCookie.contains("Path=/api/v1/admin"))
        assertTrue(setCookie.contains("SameSite=Strict"))
        assertTrue(setCookie.contains("Max-Age=28800"))
        assertTrue(setCookie.contains("Secure"))

        val token = tokenFrom(setCookie)
        assertEquals(43, token.length)
        val digests = jdbcTemplate.queryForList("SELECT session_token_digest FROM admin_sessions", String::class.java)
        assertEquals(listOf(AdminSessionAccessVerifier.digest(token)), digests)
        assertFalse(digests.contains(token))
    }

    @Test
    fun `비밀번호가 틀리면 401 ADMIN_CREDENTIAL_INVALID이다`() {
        login(password = "wrong-password", address = "10.0.1.2")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_CREDENTIAL_INVALID.code))
    }

    @Test
    fun `없는 username도 401 ADMIN_CREDENTIAL_INVALID이다`() {
        login(username = "unknown", address = "10.0.1.3")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_CREDENTIAL_INVALID.code))
    }

    @Test
    fun `username이나 password가 비어 있으면 400이다`() {
        listOf(
            """{"username":"","password":"$PASSWORD"}""",
            """{"username":"$USERNAME","password":"  "}""",
            """{"password":"$PASSWORD"}""",
            """{"username":"$USERNAME"}""",
        ).forEach { body ->
            mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.code))
        }
    }

    @Test
    fun `연속 실패하면 credential 검증 전에 429 ADMIN_LOGIN_RATE_LIMITED이다`() {
        repeat(5) {
            login(password = "wrong-password", address = "10.0.2.1").andExpect(status().isUnauthorized)
        }

        login(address = "10.0.2.1")
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_LOGIN_RATE_LIMITED.code))
    }

    @Test
    fun `username 대소문자와 공백을 무시하고 같은 키로 제한한다`() {
        repeat(5) {
            login(username = "  ADMIN ", password = "wrong-password", address = "10.0.2.2")
                .andExpect(status().isUnauthorized)
        }

        login(username = "admin", password = "wrong-password", address = "10.0.2.2")
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `로그인 성공은 시도 횟수를 초기화한다`() {
        repeat(4) { login(password = "wrong-password", address = "10.0.2.3").andExpect(status().isUnauthorized) }
        login(address = "10.0.2.3").andExpect(status().isOk)

        repeat(5) { login(password = "wrong-password", address = "10.0.2.3").andExpect(status().isUnauthorized) }
        login(password = "wrong-password", address = "10.0.2.3").andExpect(status().isTooManyRequests)
    }

    @Test
    fun `로그아웃은 204와 쿠키 삭제 헤더를 반환하고 같은 쿠키로 다시 로그아웃하면 401이다`() {
        val token = loginToken("10.0.3.1")

        val result = mockMvc.perform(post(LOGOUT_PATH).cookie(sessionCookie(token)))
            .andExpect(status().isNoContent)
            .andReturn()

        val setCookie = requireNotNull(result.response.getHeader("Set-Cookie"))
        assertTrue(setCookie.startsWith("POPPY_ADMIN_SESSION=;"))
        assertTrue(setCookie.contains("Max-Age=0"))
        assertTrue(setCookie.contains("Path=/api/v1/admin"))
        assertTrue(setCookie.contains("HttpOnly"))
        val revokedAt = jdbcTemplate.queryForList("SELECT revoked_at FROM admin_sessions").single()["revoked_at"]
        assertNotNull(revokedAt)

        mockMvc.perform(post(LOGOUT_PATH).cookie(sessionCookie(token)))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_SESSION_INVALID.code))
    }

    @Test
    fun `쿠키가 없으면 로그아웃은 401 ADMIN_SESSION_INVALID이다`() {
        mockMvc.perform(post(LOGOUT_PATH))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_SESSION_INVALID.code))
    }

    @Test
    fun `위조된 토큰은 401 ADMIN_SESSION_INVALID이다`() {
        mockMvc.perform(post(LOGOUT_PATH).cookie(sessionCookie("forged-token-value")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_SESSION_INVALID.code))
    }

    @Test
    fun `만료된 세션은 401 ADMIN_SESSION_INVALID이다`() {
        val token = "expired-session-token"
        insertSession(token, expiresAt = Instant.now().minusSeconds(1))

        mockMvc.perform(post(LOGOUT_PATH).cookie(sessionCookie(token)))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_SESSION_INVALID.code))
    }

    @Test
    fun `boundary는 쿠키 없이 401이고 유효한 쿠키로 통과한다`() {
        mockMvc.perform(get(PROBE_PATH))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_SESSION_INVALID.code))

        val token = loginToken("10.0.4.1")

        mockMvc.perform(get(PROBE_PATH).cookie(sessionCookie(token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.adminSessionId").isNotEmpty)
    }

    @Test
    fun `기존 admin API도 boundary가 보호한다`() {
        mockMvc.perform(get("/api/v1/admin/robots"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_SESSION_INVALID.code))

        val token = loginToken("10.0.4.3")

        mockMvc.perform(get("/api/v1/admin/robots").cookie(sessionCookie(token)))
            .andExpect(status().isOk)
    }

    @Test
    fun `로그아웃된 세션으로는 boundary를 통과하지 못한다`() {
        val token = loginToken("10.0.4.2")
        mockMvc.perform(post(LOGOUT_PATH).cookie(sessionCookie(token))).andExpect(status().isNoContent)

        mockMvc.perform(get(PROBE_PATH).cookie(sessionCookie(token)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `password와 토큰과 해시는 로그에 남지 않는다`(output: CapturedOutput) {
        val token = loginToken("10.0.5.1")
        login(password = WRONG_PASSWORD, address = "10.0.5.2").andExpect(status().isUnauthorized)
        mockMvc.perform(post(LOGOUT_PATH).cookie(sessionCookie(token))).andExpect(status().isNoContent)
        mockMvc.perform(post(LOGOUT_PATH).cookie(sessionCookie(token))).andExpect(status().isUnauthorized)

        val logs = output.all
        assertFalse(logs.contains(PASSWORD))
        assertFalse(logs.contains(WRONG_PASSWORD))
        assertFalse(logs.contains(token))
        assertFalse(logs.contains(PASSWORD_HASH))
        assertFalse(logs.contains("Using generated security password"))
    }

    @Test
    fun `로그인 요청 DTO의 toString은 비밀번호를 노출하지 않는다`() {
        val request = AdminLoginRequest(username = USERNAME, password = PASSWORD)

        assertFalse(request.toString().contains(PASSWORD))
    }

    private fun login(
        username: String = USERNAME,
        password: String = PASSWORD,
        address: String = "10.0.9.9",
    ): ResultActions = mockMvc.perform(
        post(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$username","password":"$password"}""")
            .with { request ->
                request.remoteAddr = address
                request
            },
    )

    private fun loginToken(address: String): String {
        val result = login(address = address).andExpect(status().isOk).andReturn()
        return tokenFrom(requireNotNull(result.response.getHeader("Set-Cookie")))
    }

    private fun tokenFrom(setCookie: String): String =
        setCookie.substringAfter("POPPY_ADMIN_SESSION=").substringBefore(";")

    private fun sessionCookie(token: String) = Cookie("POPPY_ADMIN_SESSION", token)

    private fun insertSession(token: String, expiresAt: Instant) {
        jdbcTemplate.update(
            "INSERT INTO admin_sessions (id, session_token_digest, created_at, expires_at) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            AdminSessionAccessVerifier.digest(token),
            Timestamp.from(expiresAt.minusSeconds(3600)),
            Timestamp.from(expiresAt),
        )
    }

    companion object {
        private const val LOGIN_PATH = "/api/v1/admin/auth/login"
        private const val LOGOUT_PATH = "/api/v1/admin/auth/logout"
        private const val PROBE_PATH = "/api/v1/admin/boundary-probe"
        private const val USERNAME = "admin"
        private const val PASSWORD = "integration-admin-password"
        private const val WRONG_PASSWORD = "integration-wrong-password"
        private val PASSWORD_HASH: String = requireNotNull(BCryptPasswordEncoder(4).encode(PASSWORD))

        @DynamicPropertySource
        @JvmStatic
        fun registerAdminProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.hikari.maximum-pool-size") { 2 }
            registry.add("spring.datasource.hikari.minimum-idle") { 1 }
            registry.add("poppy.admin.username") { USERNAME }
            registry.add("poppy.admin.password-hash") { PASSWORD_HASH }
        }
    }
}
