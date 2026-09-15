package team.inreok.poppyserver.domain.session.infrastructure

import jakarta.persistence.EntityManager
import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.model.Session

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionPersistenceAdapter(
    private val sessionJpaRepository: SessionJpaRepository,
    private val entityManager: EntityManager,
) : SessionRepository {
    override fun save(session: Session): Session {
        val existing = sessionJpaRepository.findById(session.id).orElse(null)
        val entity = existing ?: SessionEntity(id = session.id)
        entity.updateFrom(session)
        if (existing == null) {
            entityManager.persist(entity)
        }
        entityManager.flush()
        return entity.toDomain()
    }

    override fun findById(id: UUID): Session? = sessionJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByIdForUpdate(id: UUID): Session? = sessionJpaRepository.findByIdForUpdate(id)?.toDomain()

    override fun findByTokenDigest(tokenDigest: String): Session? =
        sessionJpaRepository.findBySessionTokenDigest(tokenDigest)?.toDomain()

    override fun findByRecoveryCodeDigest(recoveryCodeDigest: String): Session? =
        sessionJpaRepository.findByRecoveryCodeDigest(recoveryCodeDigest)?.toDomain()

    private fun SessionEntity.updateFrom(session: Session) {
        id = session.id
        currentBlockVersion = session.currentBlockVersion
        createdAt = session.createdAt
        sessionTokenDigest = session.sessionTokenDigest
        recoveryCodeDigest = session.recoveryCodeDigest
        lastActivityAt = session.lastActivityAt
        expiredAt = session.expiredAt
    }

    private fun SessionEntity.toDomain(): Session = Session.restore(
        id = requireNotNull(id),
        currentBlockVersion = currentBlockVersion,
        createdAt = createdAt,
        sessionTokenDigest = sessionTokenDigest,
        recoveryCodeDigest = recoveryCodeDigest,
        lastActivityAt = lastActivityAt,
        expiredAt = expiredAt,
    )

    override fun findInactiveIdsBefore(cutoff: Instant): List<UUID> =
        sessionJpaRepository.findInactiveIdsBefore(cutoff)
}
