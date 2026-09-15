package team.inreok.poppyserver.domain.session.application

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionExpirationService(
    private val sessionRepository: SessionRepository,
    private val sessionExpirationTransaction: SessionExpirationTransaction,
    @Value("\${poppy.session.inactivity-timeout:PT1H}")
    private val inactivityTimeout: Duration,
    private val clock: Clock? = null,
) {
    init {
        require(!inactivityTimeout.isNegative && !inactivityTimeout.isZero) {
            "Session inactivity timeout must be positive"
        }
    }

    fun expireInactiveSessions(now: Instant = Instant.now(clock ?: Clock.systemUTC())): Int {
        val cutoff = now.minus(inactivityTimeout)
        return sessionRepository.findInactiveIdsBefore(cutoff).count { expireIfInactive(it, cutoff, now) }
    }

    private fun expireIfInactive(sessionId: UUID, cutoff: Instant, now: Instant): Boolean =
        sessionExpirationTransaction.expireIfInactive(sessionId, cutoff, now)
}

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionExpirationTransaction(
    private val sessionRepository: SessionRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun expireIfInactive(sessionId: UUID, cutoff: Instant, now: Instant): Boolean {
        val session = sessionRepository.findByIdForUpdate(sessionId) ?: return false
        if (session.expiredAt != null || session.lastActivityAt.isAfter(cutoff)) {
            return false
        }
        session.expire(now)
        sessionRepository.save(session)
        return true
    }
}
