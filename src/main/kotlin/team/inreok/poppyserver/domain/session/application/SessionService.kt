package team.inreok.poppyserver.domain.session.application

import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.session.model.BlockRevision
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionService(
    private val sessionRepository: SessionRepository,
    private val blockRevisionRepository: BlockRevisionRepository,
    private val sessionAccessVerifier: SessionAccessVerifier,
) {
    @Transactional
    fun createSession(): SessionCreationResult {
        val issuedToken = sessionAccessVerifier.issue()
        val session = sessionRepository.save(Session.createWithToken(issuedToken.digest))
        return SessionCreationResult(
            sessionId = session.id,
            currentBlockVersion = session.currentBlockVersion,
            sessionToken = issuedToken.raw,
        )
    }

    @Transactional
    fun appendBlockRevision(sessionId: UUID, document: String): BlockRevisionAppendResult {
        val session = sessionRepository.findByIdForUpdate(sessionId)
            ?: throw ApplicationException(ErrorCode.SESSION_NOT_FOUND)
        val version = session.advanceBlockVersion()
        val revision = BlockRevision.create(
            sessionId = session.id,
            version = version,
            document = document,
            createdAt = Instant.now(),
        )
        blockRevisionRepository.save(revision)
        sessionRepository.save(session)
        return BlockRevisionAppendResult(
            sessionId = revision.sessionId,
            blockVersion = revision.version,
        )
    }
}

data class SessionCreationResult(
    val sessionId: UUID,
    val currentBlockVersion: Long,
    val sessionToken: String,
)

data class BlockRevisionAppendResult(
    val sessionId: UUID,
    val blockVersion: Long,
)
