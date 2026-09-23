package team.inreok.poppyserver.domain.admin.application

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.admin.model.AdminSession
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AdminSessionService(
    private val adminSessionRepository: AdminSessionRepository,
    private val adminSessionAccessVerifier: AdminSessionAccessVerifier,
    @Value("\${poppy.admin.session.ttl:PT8H}") private val ttl: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        require(!ttl.isNegative && !ttl.isZero) { "Admin session ttl must be positive" }
    }

    @Transactional
    fun open(): OpenedAdminSession {
        val issuedToken = adminSessionAccessVerifier.issue()
        val now = Instant.now(clock)
        adminSessionRepository.save(
            AdminSession.open(
                sessionTokenDigest = issuedToken.digest,
                createdAt = now,
                expiresAt = now.plus(ttl),
            ),
        )
        return OpenedAdminSession(token = issuedToken.raw, ttl = ttl)
    }

    @Transactional
    fun revoke(adminSessionId: UUID) {
        if (!adminSessionRepository.revokeIfActive(adminSessionId, Instant.now(clock))) {
            throw ApplicationException(ErrorCode.ADMIN_SESSION_INVALID)
        }
    }

    @Transactional
    fun deleteInactive(): Int = adminSessionRepository.deleteInactive(Instant.now(clock))
}

class OpenedAdminSession(
    val token: String,
    val ttl: Duration,
) {
    override fun toString(): String = "OpenedAdminSession(ttl=$ttl)"
}
