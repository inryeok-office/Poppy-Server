package team.inreok.poppyserver.domain.session.infrastructure

import java.time.Instant
import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SessionJpaRepository : JpaRepository<SessionEntity, UUID> {
    fun findBySessionTokenDigest(sessionTokenDigest: String): SessionEntity?

    fun findByRecoveryCodeDigest(recoveryCodeDigest: String): SessionEntity?

    @Query("select session.id from SessionEntity session where session.expiredAt is null and session.lastActivityAt <= :cutoff")
    fun findInactiveIdsBefore(@Param("cutoff") cutoff: Instant): List<UUID>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from SessionEntity session where session.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): SessionEntity?
}
