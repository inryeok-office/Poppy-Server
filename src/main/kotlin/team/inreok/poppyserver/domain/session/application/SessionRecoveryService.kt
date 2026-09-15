package team.inreok.poppyserver.domain.session.application

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionRecoveryService(
    private val sessionRepository: SessionRepository,
    private val sessionAccessVerifier: SessionAccessVerifier,
    private val recoveryAttemptRateLimiter: RecoveryAttemptRateLimiter,
) {
    @Transactional
    fun restore(rawRecoveryCode: String?, clientKey: String): SessionRecoveryResult {
        val normalized = rawRecoveryCode?.let(RecoveryCodeGenerator::normalize)
        if (normalized == null || !RecoveryCodeGenerator.isValidFormat(normalized)) {
            throw ApplicationException(ErrorCode.RECOVERY_CODE_INVALID)
        }
        recoveryAttemptRateLimiter.checkAndRecord(clientKey)
        val digest = RecoveryCodeGenerator.digest(normalized)
        val candidate = sessionRepository.findByRecoveryCodeDigest(digest)
            ?: throw ApplicationException(ErrorCode.SESSION_NOT_FOUND)
        val session = sessionRepository.findByIdForUpdate(candidate.id)
            ?: throw ApplicationException(ErrorCode.SESSION_NOT_FOUND)
        if (!sameDigest(session.recoveryCodeDigest, digest)) {
            throw ApplicationException(ErrorCode.SESSION_NOT_FOUND)
        }
        if (session.expiredAt != null) {
            throw ApplicationException(ErrorCode.SESSION_EXPIRED)
        }
        val issuedToken = sessionAccessVerifier.issue()
        session.rotateSessionToken(issuedToken.digest)
        sessionRepository.save(session)
        recoveryAttemptRateLimiter.reset(clientKey)
        return SessionRecoveryResult(
            sessionId = session.id,
            currentBlockVersion = session.currentBlockVersion,
            sessionToken = issuedToken.raw,
        )
    }

    private fun sameDigest(stored: String?, candidate: String): Boolean {
        if (stored == null) return false
        return MessageDigest.isEqual(
            stored.toByteArray(StandardCharsets.UTF_8),
            candidate.toByteArray(StandardCharsets.UTF_8),
        )
    }
}

data class SessionRecoveryResult(
    val sessionId: UUID,
    val currentBlockVersion: Long,
    val sessionToken: String,
)
