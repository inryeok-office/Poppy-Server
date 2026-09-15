package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.agent.application.AgentRepository
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnBean(AgentRepository::class, ExecutionRepository::class, RobotRepository::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AgentExecutionStatusService(
    private val agentRepository: AgentRepository,
    private val executionRepository: ExecutionRepository,
    private val robotRepository: RobotRepository,
    private val executionStatusEventPublisher: ExecutionStatusEventPublisher? = null,
) {
    @Transactional
    fun report(agentId: UUID, executionId: UUID, command: ReportExecutionStatusCommand): ExecutionStatusReport {
        agentRepository.findById(agentId)
            ?: throw ApplicationException(ErrorCode.AGENT_NOT_FOUND)

        val execution = executionRepository.findByIdForStatusUpdate(executionId)
            ?: throw ApplicationException(ErrorCode.EXECUTION_NOT_FOUND)
        val robot = robotRepository.findByIdForStatusUpdate(command.robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        if (robot.agentId != agentId) {
            throw ApplicationException(ErrorCode.AGENT_ROBOT_BINDING_MISMATCH)
        }

        val targetStatus = command.status.toExecutionStatus()
        val sameStatus = execution.status == targetStatus
        if (execution.assignedRobotId != null && execution.assignedRobotId != command.robotId) {
            throw ApplicationException(ErrorCode.EXECUTION_ROBOT_MISMATCH)
        }
        val currentAssignment = robot.currentExecutionId == executionId
        val releasedReplay = sameStatus && targetStatus.isTerminal() && execution.assignedRobotId == command.robotId
        if (!currentAssignment && !releasedReplay) {
            throw ApplicationException(ErrorCode.EXECUTION_ROBOT_MISMATCH)
        }
        var executionChanged = false
        if (execution.assignedRobotId == null) {
            execution.bindRobot(command.robotId)
            executionChanged = true
        }
        if (!sameStatus) {
            transition(execution, targetStatus)
            executionChanged = true
        }
        if (executionChanged) {
            executionRepository.save(execution)
        }
        if (targetStatus.isTerminal() && currentAssignment) {
            robot.releaseExecution(executionId)
            robotRepository.save(robot)
        }
        if (executionChanged && execution.sessionId != null) {
            executionStatusEventPublisher?.publish(
                ExecutionStatusChangedEvent(
                    executionId = execution.id,
                    sessionId = execution.sessionId,
                    status = execution.status,
                ),
            )
        }

        return ExecutionStatusReport(
            executionId = execution.id,
            robotId = command.robotId,
            status = execution.status,
        )
    }

    private fun transition(execution: Execution, targetStatus: ExecutionStatus) {
        try {
            when (targetStatus) {
                ExecutionStatus.RUNNING -> execution.start()
                ExecutionStatus.COMPLETED -> execution.complete()
                ExecutionStatus.FAILED -> execution.fail()
                else -> throw ApplicationException(ErrorCode.EXECUTION_STATUS_UNSUPPORTED)
            }
        } catch (_: IllegalStateException) {
            throw ApplicationException(ErrorCode.EXECUTION_STATUS_TRANSITION_INVALID)
        }
    }
}

data class ReportExecutionStatusCommand(
    val robotId: UUID,
    val status: ReportExecutionStatus,
)

enum class ReportExecutionStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    ;

    fun toExecutionStatus(): ExecutionStatus = when (this) {
        RUNNING -> ExecutionStatus.RUNNING
        COMPLETED -> ExecutionStatus.COMPLETED
        FAILED -> ExecutionStatus.FAILED
    }
}

data class ExecutionStatusReport(
    val executionId: UUID,
    val robotId: UUID,
    val status: ExecutionStatus,
)

private fun ExecutionStatus.isTerminal(): Boolean = this == ExecutionStatus.COMPLETED || this == ExecutionStatus.FAILED
