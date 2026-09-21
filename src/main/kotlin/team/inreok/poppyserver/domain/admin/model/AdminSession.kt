package team.inreok.poppyserver.domain.admin.model

import java.time.Instant
import java.util.UUID

class AdminSession(
    val id: UUID,
    val sessionTokenDigest: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
) {
    fun isActiveAt(at: Instant): Boolean = revokedAt == null && expiresAt.isAfter(at)

    companion object {
        fun open(sessionTokenDigest: String, createdAt: Instant, expiresAt: Instant): AdminSession = AdminSession(
            id = UUID.randomUUID(),
            sessionTokenDigest = sessionTokenDigest,
            createdAt = createdAt,
            expiresAt = expiresAt,
            revokedAt = null,
        )
    }
}
