package team.inreok.poppyserver.domain.admin.application

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import team.inreok.poppyserver.global.security.AdminSessionResolver

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AdminSessionAccessVerifier(
    private val adminSessionRepository: AdminSessionRepository,
    private val clock: Clock = Clock.systemUTC(),
) : AdminSessionResolver {
    override fun resolveAdminSessionId(token: String): UUID? {
        val tokenDigest = digest(token)
        val session = adminSessionRepository.findByTokenDigest(tokenDigest) ?: return null
        if (!sameDigest(session.sessionTokenDigest, tokenDigest)) return null
        return session.id.takeIf { session.isActiveAt(Instant.now(clock)) }
    }

    fun issue(): IssuedAdminSessionToken {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedAdminSessionToken(raw = raw, digest = digest(raw))
    }

    private fun sameDigest(stored: String, candidate: String): Boolean = MessageDigest.isEqual(
        stored.toByteArray(StandardCharsets.UTF_8),
        candidate.toByteArray(StandardCharsets.UTF_8),
    )

    companion object {
        private const val TOKEN_BYTES = 32
        private val secureRandom = SecureRandom()

        fun digest(token: String): String = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8)),
        )
    }
}

class IssuedAdminSessionToken(
    val raw: String,
    val digest: String,
) {
    override fun toString(): String = "IssuedAdminSessionToken()"
}
