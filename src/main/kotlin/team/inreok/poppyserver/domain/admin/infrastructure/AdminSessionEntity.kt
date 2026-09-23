package team.inreok.poppyserver.domain.admin.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import team.inreok.poppyserver.domain.admin.model.AdminSession

@Entity
@Table(name = "admin_sessions")
class AdminSessionEntity(
    @Id
    var id: UUID? = null,
    @Column(name = "session_token_digest", nullable = false, unique = true)
    var sessionTokenDigest: String = "",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
) {
    fun toDomain(): AdminSession = AdminSession(
        id = requireNotNull(id),
        sessionTokenDigest = sessionTokenDigest,
        createdAt = createdAt,
        expiresAt = expiresAt,
        revokedAt = revokedAt,
    )

    companion object {
        fun from(adminSession: AdminSession): AdminSessionEntity = AdminSessionEntity(
            id = adminSession.id,
            sessionTokenDigest = adminSession.sessionTokenDigest,
            createdAt = adminSession.createdAt,
            expiresAt = adminSession.expiresAt,
            revokedAt = adminSession.revokedAt,
        )
    }
}
