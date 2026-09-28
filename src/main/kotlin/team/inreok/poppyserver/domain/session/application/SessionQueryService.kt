package team.inreok.poppyserver.domain.session.application

import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.session.model.ExperienceMode
import team.inreok.poppyserver.domain.session.model.SessionStatus

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionQueryService(
    private val sessionAccessVerifier: SessionAccessVerifier,
    private val sessionExpirationService: SessionExpirationService,
) {
    @Transactional(readOnly = true)
    fun getSession(sessionId: UUID, sessionToken: String?): SessionQueryResult {
        val session = sessionAccessVerifier.verify(sessionId, sessionToken)
        return SessionQueryResult(
            sessionId = session.id,
            mode = session.mode,
            missionId = session.missionId,
            blockVersion = session.currentBlockVersion,
            status = session.status,
            lastActivityAt = session.lastActivityAt,
            expiresAt = sessionExpirationService.expiresAt(session.lastActivityAt),
        )
    }
}

data class SessionQueryResult(
    val sessionId: UUID,
    val mode: ExperienceMode?,
    val missionId: UUID?,
    val blockVersion: Long,
    val status: SessionStatus,
    val lastActivityAt: Instant,
    val expiresAt: Instant,
)
