package team.inreok.poppyserver.domain.admin.application

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AdminLoginAttemptRateLimiter(
    @Value("\${poppy.admin.login.attempt-window:PT1M}") windowDuration: Duration,
    @Value("\${poppy.admin.login.max-attempts:5}") private val maxAttempts: Int,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val windows = ConcurrentHashMap<String, AttemptWindow>()
    private val window = windowDuration

    init {
        require(!windowDuration.isNegative && !windowDuration.isZero)
        require(maxAttempts > 0)
    }

    fun checkAndRecord(key: String) {
        val now = Instant.now(clock)
        val state = windows.compute(key) { _, current ->
            val active = if (current == null || !now.isBefore(current.startedAt.plus(window))) {
                AttemptWindow(now, 0)
            } else {
                current
            }
            active.copy(attempts = active.attempts + 1)
        }!!
        if (state.attempts > maxAttempts) {
            throw ApplicationException(ErrorCode.ADMIN_LOGIN_RATE_LIMITED)
        }
    }

    fun reset(key: String) {
        windows.remove(key)
    }

    @Scheduled(fixedDelayString = "\${poppy.admin.login.attempt-cleanup-interval-milliseconds:60000}")
    fun cleanupExpiredEntries() {
        cleanupExpiredEntries(Instant.now(clock))
    }

    internal fun cleanupExpiredEntries(now: Instant): Int {
        var removed = 0
        windows.entries.removeIf { entry ->
            if (!now.isBefore(entry.value.startedAt.plus(window))) {
                removed += 1
                true
            } else {
                false
            }
        }
        return removed
    }

    internal fun entryCount(): Int = windows.size

    private data class AttemptWindow(val startedAt: Instant, val attempts: Int)
}
