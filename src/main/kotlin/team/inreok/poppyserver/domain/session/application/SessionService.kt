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
        val issuedRecoveryCode = issueRecoveryCode()
        val session = sessionRepository.save(
            Session.createWithToken(
                sessionTokenDigest = issuedToken.digest,
                recoveryCodeDigest = issuedRecoveryCode.digest,
            ),
        )
        return SessionCreationResult(
            sessionId = session.id,
            currentBlockVersion = session.currentBlockVersion,
            sessionToken = issuedToken.raw,
            recoveryCode = issuedRecoveryCode.raw,
        )
    }

    private fun issueRecoveryCode(): IssuedRecoveryCode {
        repeat(MAX_RECOVERY_CODE_ISSUE_ATTEMPTS) {
            val candidate = RecoveryCodeGenerator.issue()
            if (sessionRepository.findByRecoveryCodeDigest(candidate.digest) == null) {
                return candidate
            }
        }
        error("Unable to issue a unique recovery code")
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
    val recoveryCode: String,
)

data class BlockRevisionAppendResult(
    val sessionId: UUID,
    val blockVersion: Long,
)

private const val MAX_RECOVERY_CODE_ISSUE_ATTEMPTS = 5
