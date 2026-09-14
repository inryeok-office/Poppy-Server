package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

interface ExecutionDeliveryQueryRepository {
    fun findByRobotId(robotId: UUID): ExecutionDeliveryAssignment?
}

data class ExecutionDeliveryAssignment(
    val robotId: UUID,
    val agentId: UUID?,
    val currentExecutionId: UUID?,
    val executionId: UUID?,
    val executionStatus: ExecutionStatus?,
)
