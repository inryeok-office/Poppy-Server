package team.inreok.poppyserver.domain.admin.application

import java.time.Instant
import java.util.UUID
import team.inreok.poppyserver.domain.admin.model.AdminSession

interface AdminSessionRepository {
    fun save(adminSession: AdminSession): AdminSession

    fun findByTokenDigest(tokenDigest: String): AdminSession?

    fun revokeIfActive(id: UUID, at: Instant): Boolean

    fun deleteInactive(at: Instant): Int
}
