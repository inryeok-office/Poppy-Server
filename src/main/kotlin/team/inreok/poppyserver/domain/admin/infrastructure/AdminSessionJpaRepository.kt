package team.inreok.poppyserver.domain.admin.infrastructure

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AdminSessionJpaRepository : JpaRepository<AdminSessionEntity, UUID> {
    fun findBySessionTokenDigest(sessionTokenDigest: String): AdminSessionEntity?

    @Modifying
    @Query(
        "update AdminSessionEntity session set session.revokedAt = :at " +
            "where session.id = :id and session.revokedAt is null and session.expiresAt > :at",
    )
    fun revokeIfActive(@Param("id") id: UUID, @Param("at") at: Instant): Int

    @Modifying
    @Query("delete from AdminSessionEntity session where session.revokedAt is not null or session.expiresAt <= :at")
    fun deleteInactive(@Param("at") at: Instant): Int
}
