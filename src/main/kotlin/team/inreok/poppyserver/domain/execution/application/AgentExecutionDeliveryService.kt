package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.agent.application.AgentRepository
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnBean(AgentRepository::class, RobotRepository::class, ExecutionRepository::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AgentExecutionDeliveryService(
    private val agentRepository: AgentRepository,
    private val robotRepository: RobotRepository,
    private val executionRepository: ExecutionRepository,
) {
    @Transactional(readOnly = true)
    fun findNext(agentId: UUID, robotId: UUID): AgentExecutionDelivery? {
        agentRepository.findById(agentId)
            ?: throw ApplicationException(ErrorCode.AGENT_NOT_FOUND)
        val robot = robotRepository.findById(robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        if (robot.agentId != agentId) {
            throw ApplicationException(ErrorCode.AGENT_ROBOT_BINDING_MISMATCH)
        }
        val executionId = robot.currentExecutionId ?: return null
        val execution = executionRepository.findById(executionId)
            ?: throw ApplicationException(ErrorCode.EXECUTION_DELIVERY_INVARIANT_VIOLATED)
        if (execution.status != ExecutionStatus.ASSIGNED) {
            throw ApplicationException(ErrorCode.EXECUTION_DELIVERY_INVARIANT_VIOLATED)
        }
        return AgentExecutionDelivery(
            executionId = execution.id,
            robotId = robot.id,
            status = execution.status,
            protocolVersion = PROTOCOL_VERSION,
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
)
