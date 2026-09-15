package team.inreok.poppyserver.domain.session.application

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import team.inreok.poppyserver.global.error.ApplicationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecoveryAttemptRateLimiterTest {
    @Test
    fun `expired entries are removed without scanning on every request`() {
        val clock = MutableTestClock(Instant.parse("2026-09-15T00:00:00Z"))
        val limiter = RecoveryAttemptRateLimiter(Duration.ofMinutes(1), 5, clock)

        limiter.checkAndRecord("stale")
        clock.current = clock.current.plusSeconds(30)
        limiter.checkAndRecord("active")

        assertEquals(2, limiter.entryCount())
        assertEquals(0, limiter.cleanupExpiredEntries(clock.current))
        assertEquals(2, limiter.entryCount())

        clock.current = clock.current.plusSeconds(31)
        assertEquals(1, limiter.cleanupExpiredEntries(clock.current))
        assertEquals(1, limiter.entryCount())
    }

    @Test
    fun `five attempts remain allowed and the sixth is limited`() {
        val clock = MutableTestClock(Instant.parse("2026-09-15T00:00:00Z"))
        val limiter = RecoveryAttemptRateLimiter(Duration.ofMinutes(1), 5, clock)

        repeat(5) { limiter.checkAndRecord("client") }

        assertFailsWith<ApplicationException> {
            limiter.checkAndRecord("client")
        }
    }

    private class MutableTestClock(var current: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = current
    }
}
