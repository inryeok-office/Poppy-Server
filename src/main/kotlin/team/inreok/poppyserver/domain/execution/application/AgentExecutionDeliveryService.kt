package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.agent.application.AgentRepository
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnBean(AgentRepository::class, ExecutionDeliveryQueryRepository::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AgentExecutionDeliveryService(
    private val agentRepository: AgentRepository,
    private val executionDeliveryQueryRepository: ExecutionDeliveryQueryRepository,
) {
    @Transactional(readOnly = true)
    fun findNext(agentId: UUID, robotId: UUID): AgentExecutionDelivery? {
        agentRepository.findById(agentId)
            ?: throw ApplicationException(ErrorCode.AGENT_NOT_FOUND)
        val assignment = executionDeliveryQueryRepository.findByRobotId(robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        if (assignment.agentId != agentId) {
            throw ApplicationException(ErrorCode.AGENT_ROBOT_BINDING_MISMATCH)
        }
        val executionId = assignment.currentExecutionId ?: return null
        if (assignment.executionId != executionId || assignment.executionStatus == null) {
            throw ApplicationException(ErrorCode.EXECUTION_DELIVERY_INVARIANT_VIOLATED)
        }
        if (assignment.executionStatus != ExecutionStatus.ASSIGNED) {
            throw ApplicationException(ErrorCode.EXECUTION_DELIVERY_INVARIANT_VIOLATED)
        }
        val commandPayload = assignment.compiledCommandPayload
            ?: throw ApplicationException(ErrorCode.EXECUTION_DELIVERY_INVARIANT_VIOLATED)
        if (commandPayload.isBlank()) {
            throw ApplicationException(ErrorCode.EXECUTION_DELIVERY_INVARIANT_VIOLATED)
        }
        return AgentExecutionDelivery(
            executionId = executionId,
            robotId = assignment.robotId,
            status = assignment.executionStatus,
            protocolVersion = PROTOCOL_VERSION,
            commandPayload = commandPayload,
        )
    }

    companion object {
        private const val PROTOCOL_VERSION = 1
    }
}

data class AgentExecutionDelivery(
    val executionId: UUID,
    val robotId: UUID,
    val status: ExecutionStatus,
    val protocolVersion: Int,
    val commandPayload: String,
)
