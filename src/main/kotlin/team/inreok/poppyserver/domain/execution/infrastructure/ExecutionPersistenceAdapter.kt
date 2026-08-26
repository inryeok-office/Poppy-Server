package team.inreok.poppyserver.domain.execution.infrastructure

import jakarta.persistence.EntityManager
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.model.Execution

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ExecutionPersistenceAdapter(
    private val executionJpaRepository: ExecutionJpaRepository,
    private val entityManager: EntityManager,
) : ExecutionRepository {
    override fun save(execution: Execution): Execution {
        val existing = executionJpaRepository.findById(execution.id).orElse(null)
        val entity = existing ?: ExecutionEntity(id = execution.id)
        entity.updateFrom(execution)
        if (existing == null) {
            entityManager.persist(entity)
        }
        entityManager.flush()
        return entity.toDomain()
    }

    override fun findById(id: UUID): Execution? = executionJpaRepository.findById(id).orElse(null)?.toDomain()

    private fun ExecutionEntity.updateFrom(execution: Execution) {
        id = execution.id
        status = execution.status
    }

    private fun ExecutionEntity.toDomain(): Execution = Execution.restore(
        id = requireNotNull(id),
        status = status,
    )
}
