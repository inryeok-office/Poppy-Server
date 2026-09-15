package team.inreok.poppyserver.domain.session.application

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class RecoveryAttemptRateLimiter(
    @Value("\${poppy.session.recovery-attempt-window:PT1M}") windowDuration: Duration,
    @Value("\${poppy.session.recovery-max-attempts:5}") private val maxAttempts: Int,
    private val clock: Clock? = null,
) {
    private val windows = ConcurrentHashMap<String, AttemptWindow>()
    private val window = windowDuration

    init {
        require(!windowDuration.isNegative && !windowDuration.isZero)
        require(maxAttempts > 0)
    }

    fun checkAndRecord(key: String) {
        val now = Instant.now(clock ?: Clock.systemUTC())
        val state = windows.compute(key) { _, current ->
            val active = if (current == null || !now.isBefore(current.startedAt.plus(window))) {
                AttemptWindow(now, 0)
            } else {
                current
            }
            active.copy(attempts = active.attempts + 1)
        }!!
        if (state.attempts > maxAttempts) {
            throw ApplicationException(ErrorCode.RECOVERY_ATTEMPT_RATE_LIMITED)
        }
    }

    fun reset(key: String) {
        windows.remove(key)
    }

    private data class AttemptWindow(val startedAt: Instant, val attempts: Int)
}
