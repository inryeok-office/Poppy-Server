package team.inreok.poppyserver.domain.execution.application

import java.util.UUID

interface QueuedExecutionQueryRepository {
    fun findQueuedExecutions(): List<QueuedExecutionRef>
}

data class QueuedExecutionRef(
    val executionId: UUID,
    val requestedRobotId: UUID?,
)
