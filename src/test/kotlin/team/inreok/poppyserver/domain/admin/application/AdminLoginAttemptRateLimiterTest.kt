package team.inreok.poppyserver.domain.admin.application

import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.support.MutableTestClock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminLoginAttemptRateLimiterTest {
    private val clock = MutableTestClock(Instant.parse("2026-09-21T00:00:00Z"))
    private val limiter = AdminLoginAttemptRateLimiter(Duration.ofMinutes(1), 5, clock)

    @Test
    fun `최대 시도 횟수까지 허용하고 초과하면 ADMIN_LOGIN_RATE_LIMITED를 던진다`() {
        repeat(5) { limiter.checkAndRecord("client:admin") }

        val exception = assertFailsWith<ApplicationException> { limiter.checkAndRecord("client:admin") }

        assertEquals(ErrorCode.ADMIN_LOGIN_RATE_LIMITED, exception.errorCode)
    }

    @Test
    fun `윈도우가 지나면 다시 허용한다`() {
        repeat(5) { limiter.checkAndRecord("client:admin") }

        clock.current = clock.current.plusSeconds(60)

        limiter.checkAndRecord("client:admin")
    }

    @Test
    fun `reset하면 시도 횟수가 초기화된다`() {
        repeat(5) { limiter.checkAndRecord("client:admin") }

        limiter.reset("client:admin")

        repeat(5) { limiter.checkAndRecord("client:admin") }
    }

    @Test
    fun `키가 다르면 시도 횟수를 공유하지 않는다`() {
        repeat(5) { limiter.checkAndRecord("client:admin") }

        limiter.checkAndRecord("client:other")
    }

    @Test
    fun `만료된 항목만 정리한다`() {
        limiter.checkAndRecord("stale")
        clock.current = clock.current.plusSeconds(30)
        limiter.checkAndRecord("active")

        assertEquals(0, limiter.cleanupExpiredEntries(clock.current))
        assertEquals(2, limiter.entryCount())

        clock.current = clock.current.plusSeconds(31)
        assertEquals(1, limiter.cleanupExpiredEntries(clock.current))
        assertEquals(1, limiter.entryCount())
    }
}
