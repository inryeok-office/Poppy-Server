package team.inreok.poppyserver.domain.execution.infrastructure

import jakarta.persistence.EntityManager
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.application.QueuedExecutionQueryRepository
import team.inreok.poppyserver.domain.execution.application.QueuedExecutionRef
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class QueuedExecutionQueryPersistenceAdapter(
    private val entityManager: EntityManager,
) : QueuedExecutionQueryRepository {
    @Transactional(readOnly = true)
    override fun findQueuedExecutions(): List<QueuedExecutionRef> = entityManager
        .createQuery(
            "select execution.id, execution.requestedRobotId from ExecutionEntity execution " +
                "where execution.status = :status " +
                "and execution.queuedAt is not null " +
                "order by execution.queuedAt asc, execution.id asc",
            Array<Any?>::class.java,
        )
        .setParameter("status", ExecutionStatus.QUEUED)
        .resultList
        .map { row -> QueuedExecutionRef(executionId = row[0] as UUID, requestedRobotId = row[1] as UUID?) }
}
