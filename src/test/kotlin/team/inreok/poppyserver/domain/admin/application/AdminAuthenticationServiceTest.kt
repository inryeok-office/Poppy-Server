package team.inreok.poppyserver.domain.admin.application

import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
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
        adminLoginAttemptRateLimiter = AdminLoginAttemptRateLimiter(Duration.ofMinutes(1), 2, 3, clock),
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

    @Test
    fun `IP 제한에 걸리면 credential 검증을 호출하지 않는다`() {
        val countingEncoder = CountingEncoder()
        val limited = AdminAuthenticationService(
            adminCredentialVerifier = AdminCredentialVerifier(
                "admin",
                requireNotNull(countingEncoder.encode(PASSWORD)),
                countingEncoder,
            ),
            adminSessionService = AdminSessionService(
                repository,
                AdminSessionAccessVerifier(repository, clock),
                Duration.ofHours(8),
                clock,
            ),
            adminLoginAttemptRateLimiter = AdminLoginAttemptRateLimiter(Duration.ofMinutes(1), 2, 3, clock),
        )
        repeat(3) { index -> assertFailsWith<ApplicationException> { limited.login("user$index", "wrong", "127.0.0.3") } }
        val callsBefore = countingEncoder.matchCalls

        val exception = assertFailsWith<ApplicationException> { limited.login("another", "wrong", "127.0.0.3") }

        assertEquals(ErrorCode.ADMIN_LOGIN_RATE_LIMITED, exception.errorCode)
        assertEquals(callsBefore, countingEncoder.matchCalls)
    }

    private class CountingEncoder(private val delegate: PasswordEncoder = BCryptPasswordEncoder(4)) : PasswordEncoder {
        var matchCalls = 0

        override fun encode(rawPassword: CharSequence?): String? = delegate.encode(rawPassword)

        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            matchCalls += 1
            return delegate.matches(rawPassword, encodedPassword)
        }
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
