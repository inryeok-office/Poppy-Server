package team.inreok.poppyserver.domain.session.application

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionAccessVerifier(
    private val sessionRepository: SessionRepository,
    private val sessionActivityService: SessionActivityService,
) {
    fun issue(): IssuedSessionToken {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedSessionToken(raw = raw, digest = digest(raw))
    }

    fun verify(sessionId: UUID, rawToken: String?): Session {
        val session = sessionRepository.findById(sessionId)
            ?: throw ApplicationException(ErrorCode.SESSION_NOT_FOUND)
        if (!matches(session.sessionTokenDigest, rawToken)) {
            throw ApplicationException(ErrorCode.SESSION_TOKEN_INVALID)
        }
        ensureActive(session)
        sessionActivityService.touch(session.id)
        return session
    }

    fun verifyOwnership(sessionId: UUID, rawToken: String?) {
        val authenticatedSession = authenticate(rawToken)
        if (authenticatedSession.id != sessionId) {
            throw ApplicationException(ErrorCode.EXECUTION_ACCESS_DENIED)
        }
        sessionActivityService.touch(authenticatedSession.id)
    }

    fun authenticate(rawToken: String?): Session {
        if (rawToken.isNullOrBlank()) {
            throw ApplicationException(ErrorCode.SESSION_TOKEN_INVALID)
        }
        val tokenDigest = digest(rawToken)
        val session = sessionRepository.findByTokenDigest(tokenDigest)
            ?: throw ApplicationException(ErrorCode.SESSION_TOKEN_INVALID)
        if (!matches(session.sessionTokenDigest, rawToken)) {
            throw ApplicationException(ErrorCode.SESSION_TOKEN_INVALID)
        }
        ensureActive(session)
        return session
    }

    private fun ensureActive(session: Session) {
        if (session.expiredAt != null) {
            throw ApplicationException(ErrorCode.SESSION_EXPIRED)
        }
    }

    private fun matches(storedDigest: String?, rawToken: String?): Boolean {
        if (storedDigest == null || rawToken.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            storedDigest.toByteArray(StandardCharsets.UTF_8),
            digest(rawToken).toByteArray(StandardCharsets.UTF_8),
        )
    }

    companion object {
        private const val TOKEN_BYTES = 32
        private val secureRandom = SecureRandom()

        fun digest(token: String): String = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8)),
        )
    }
}

data class IssuedSessionToken(
    val raw: String,
    val digest: String,
)
