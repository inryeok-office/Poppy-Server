package team.inreok.poppyserver.domain.execution.infrastructure

import jakarta.persistence.EntityManager
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.application.QueuedExecutionQueryRepository
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class QueuedExecutionQueryPersistenceAdapter(
    private val entityManager: EntityManager,
) : QueuedExecutionQueryRepository {
    @Transactional(readOnly = true)
    override fun findNextQueuedExecutionId(): UUID? = entityManager
        .createQuery(
            "select execution.id from ExecutionEntity execution " +
                "where execution.status = :status " +
                "and execution.queuedAt is not null " +
                "order by execution.queuedAt asc, execution.id asc",
            UUID::class.java,
        )
        .setParameter("status", ExecutionStatus.QUEUED)
        .setMaxResults(1)
        .resultList
        .firstOrNull()
}
