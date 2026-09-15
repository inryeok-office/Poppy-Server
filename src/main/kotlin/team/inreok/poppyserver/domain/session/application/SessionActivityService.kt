package team.inreok.poppyserver.domain.session.application

import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionActivityService(
    private val sessionRepository: SessionRepository,
    private val clock: Clock? = null,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun touch(sessionId: UUID, at: Instant = Instant.now(clock ?: Clock.systemUTC())) {
        val session = sessionRepository.findByIdForUpdate(sessionId)
            ?: throw ApplicationException(ErrorCode.SESSION_NOT_FOUND)
        if (session.expiredAt != null) {
            throw ApplicationException(ErrorCode.SESSION_EXPIRED)
        }
        session.touchActivity(at)
        sessionRepository.save(session)
    }
}
