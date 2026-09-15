package team.inreok.poppyserver.domain.execution.application

import java.time.Instant
import java.util.UUID
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

interface ExecutionStatusQueryRepository {
    fun findById(executionId: UUID): ExecutionStatusView?

    fun findActiveExecutionsBySessionId(sessionId: UUID): List<ExecutionStatusView>

    fun findActiveExecutions(): List<ExecutionStatusView>
}

data class ExecutionStatusView(
    val executionId: UUID,
    val sessionId: UUID?,
    val blockVersion: Long?,
    val status: ExecutionStatus,
    val queuePosition: Int?,
    val assignedRobotId: UUID?,
    val queuedAt: Instant?,
    val startedAt: Instant?,
    val finishedAt: Instant?,
)
