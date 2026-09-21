package team.inreok.poppyserver.domain.admin.application

import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import team.inreok.poppyserver.domain.admin.model.AdminSession
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.support.MutableTestClock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminAuthenticationServiceTest {
    private val clock = MutableTestClock(Instant.parse("2026-09-21T00:00:00Z"))
    private val encoder = BCryptPasswordEncoder(4)
    private val repository = FailingAdminSessionRepository()
    private val service = AdminAuthenticationService(
        adminCredentialVerifier = AdminCredentialVerifier("admin", requireNotNull(encoder.encode(PASSWORD)), encoder),
        adminSessionService = AdminSessionService(
            repository,
            AdminSessionAccessVerifier(repository, clock),
            Duration.ofHours(8),
            clock,
        ),
        adminLoginAttemptRateLimiter = AdminLoginAttemptRateLimiter(Duration.ofMinutes(1), 2, clock),
    )

    @Test
    fun `로그인 중 예상하지 못한 예외는 ADMIN_LOGIN_FAILED로 변환한다`() {
        val exception = assertFailsWith<ApplicationException> { service.login("admin", PASSWORD, "127.0.0.1") }

        assertEquals(ErrorCode.ADMIN_LOGIN_FAILED, exception.errorCode)
    }

    @Test
    fun `로그아웃 중 예상하지 못한 예외는 ADMIN_LOGOUT_FAILED로 변환한다`() {
        val exception = assertFailsWith<ApplicationException> { service.logout(UUID.randomUUID()) }

        assertEquals(ErrorCode.ADMIN_LOGOUT_FAILED, exception.errorCode)
    }

    @Test
    fun `credential 오류는 감싸지 않고 그대로 전파한다`() {
        val exception = assertFailsWith<ApplicationException> { service.login("admin", "wrong", "127.0.0.1") }

        assertEquals(ErrorCode.ADMIN_CREDENTIAL_INVALID, exception.errorCode)
    }

    @Test
    fun `rate limit 오류는 감싸지 않고 그대로 전파한다`() {
        repeat(2) { assertFailsWith<ApplicationException> { service.login("admin", "wrong", "127.0.0.2") } }

        val exception = assertFailsWith<ApplicationException> { service.login("admin", "wrong", "127.0.0.2") }

        assertEquals(ErrorCode.ADMIN_LOGIN_RATE_LIMITED, exception.errorCode)
    }

    private class FailingAdminSessionRepository : AdminSessionRepository {
        override fun save(adminSession: AdminSession): AdminSession = unavailable()

        override fun findByTokenDigest(tokenDigest: String): AdminSession? = unavailable()

        override fun revokeIfActive(id: UUID, at: Instant): Boolean = unavailable()

        override fun deleteInactive(at: Instant): Int = unavailable()

        private fun unavailable(): Nothing = throw IllegalStateException("repository unavailable")
    }

    companion object {
        private const val PASSWORD = "unit-test-password"
    }
}
