package team.inreok.poppyserver.domain.admin.infrastructure

import jakarta.persistence.EntityManager
import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import team.inreok.poppyserver.domain.admin.application.AdminSessionRepository
import team.inreok.poppyserver.domain.admin.model.AdminSession

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AdminSessionPersistenceAdapter(
    private val adminSessionJpaRepository: AdminSessionJpaRepository,
    private val entityManager: EntityManager,
) : AdminSessionRepository {
    override fun save(adminSession: AdminSession): AdminSession {
        val entity = AdminSessionEntity.from(adminSession)
        entityManager.persist(entity)
        entityManager.flush()
        return entity.toDomain()
    }

    override fun findByTokenDigest(tokenDigest: String): AdminSession? =
        adminSessionJpaRepository.findBySessionTokenDigest(tokenDigest)?.toDomain()

    override fun revokeIfActive(id: UUID, at: Instant): Boolean =
        adminSessionJpaRepository.revokeIfActive(id, at) > 0

    override fun deleteInactive(at: Instant): Int = adminSessionJpaRepository.deleteInactive(at)
}
