package team.inreok.poppyserver.domain.session.infrastructure

import jakarta.persistence.EntityManager
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import team.inreok.poppyserver.domain.session.application.BlockRevisionRepository
import team.inreok.poppyserver.domain.session.model.BlockRevision

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class BlockRevisionPersistenceAdapter(
    private val blockRevisionJpaRepository: BlockRevisionJpaRepository,
    private val entityManager: EntityManager,
) : BlockRevisionRepository {
    override fun save(revision: BlockRevision): BlockRevision {
        val id = BlockRevisionEntityId(revision.sessionId, revision.version)
        val existing = blockRevisionJpaRepository.findById(id).orElse(null)
        check(existing == null) { "BlockRevision은 immutable하므로 기존 version을 수정할 수 없습니다" }
        val entity = BlockRevisionEntity(
            sessionId = revision.sessionId,
            version = revision.version,
        )
        entity.updateFrom(revision)
        entityManager.persist(entity)
        entityManager.flush()
        return entity.toDomain()
    }

    override fun findById(sessionId: UUID, version: Long): BlockRevision? =
        blockRevisionJpaRepository.findById(BlockRevisionEntityId(sessionId, version)).orElse(null)?.toDomain()

    private fun BlockRevisionEntity.updateFrom(revision: BlockRevision) {
        sessionId = revision.sessionId
        version = revision.version
        document = revision.document
        createdAt = revision.createdAt
    }

    private fun BlockRevisionEntity.toDomain(): BlockRevision = BlockRevision.create(
        sessionId = requireNotNull(sessionId),
        version = version,
        document = document,
        createdAt = createdAt,
    )
}
