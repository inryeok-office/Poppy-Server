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
    private val executionStatusEventPublisher: ExecutionStatusEventPublisher,
) {
    @Transactional(readOnly = true)
    fun findActive(agentId: UUID, robotId: UUID): ActiveExecutionReport? {
        requireAgentRobot(agentId, robotId)
        val robot = robotRepository.findById(robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        val executionId = robot.currentExecutionId ?: return null
        val execution = executionRepository.findById(executionId)
            ?: throw ApplicationException(ErrorCode.EXECUTION_RECOVERY_INVARIANT_VIOLATED)
        val latestRobot = robotRepository.findById(robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        if (latestRobot.currentExecutionId == null) {
            return null
        }
        if (latestRobot.currentExecutionId != executionId ||
            execution.assignedRobotId != robotId || !execution.status.isActive()
        ) {
            throw ApplicationException(ErrorCode.EXECUTION_RECOVERY_INVARIANT_VIOLATED)
        }
        return ActiveExecutionReport(execution.id, robotId, execution.status)
    }

    @Transactional
    fun recoverActive(agentId: UUID, robotId: UUID): ExecutionRecoveryReport {
        requireAgentRobot(agentId, robotId)
        val currentRobot = robotRepository.findById(robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        val executionId = currentRobot.currentExecutionId
            ?: return ExecutionRecoveryReport(robotId, null, null, null, RecoveryAction.NO_ACTIVE_EXECUTION)

        val execution = executionRepository.findByIdForStatusUpdate(executionId)
            ?: throw ApplicationException(ErrorCode.EXECUTION_RECOVERY_INVARIANT_VIOLATED)
        val robot = robotRepository.findByIdForStatusUpdate(robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        if (robot.currentExecutionId == null) {
            return ExecutionRecoveryReport(robotId, null, null, null, RecoveryAction.NO_ACTIVE_EXECUTION)
        }
        if (robot.agentId != agentId || robot.currentExecutionId != executionId ||
            execution.assignedRobotId != robotId
        ) {
            throw ApplicationException(ErrorCode.EXECUTION_RECOVERY_INVARIANT_VIOLATED)
        }
        val previousStatus = execution.status
        if (!previousStatus.isRecoverable()) {
            throw ApplicationException(ErrorCode.EXECUTION_RECOVERY_INVARIANT_VIOLATED)
        }
        execution.fail()
        robot.releaseExecution(executionId)
        executionRepository.save(execution)
        robotRepository.save(robot)
        execution.sessionId?.let { sessionId ->
            executionStatusEventPublisher.publish(
                ExecutionStatusChangedEvent(execution.id, sessionId, execution.status),
            )
        }
        return ExecutionRecoveryReport(
            robotId = robotId,
            executionId = executionId,
            previousStatus = previousStatus,
            status = execution.status,
            action = RecoveryAction.RECOVERED_AS_FAILED,
        )
    }

    @Transactional(readOnly = true)
    fun find(agentId: UUID, executionId: UUID, robotId: UUID): ExecutionStatusReport {
        agentRepository.findById(agentId)
            ?: throw ApplicationException(ErrorCode.AGENT_NOT_FOUND)
        val execution = executionRepository.findById(executionId)
            ?: throw ApplicationException(ErrorCode.EXECUTION_NOT_FOUND)
        val robot = robotRepository.findById(robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        if (robot.agentId != agentId) {
            throw ApplicationException(ErrorCode.AGENT_ROBOT_BINDING_MISMATCH)
        }
        if (execution.assignedRobotId != robotId) {
            throw ApplicationException(ErrorCode.EXECUTION_ROBOT_MISMATCH)
        }
        return ExecutionStatusReport(
            executionId = execution.id,
            robotId = robotId,
            status = execution.status,
        )
    }

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
            executionStatusEventPublisher.publish(
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
                ExecutionStatus.CANCELLED -> execution.cancel()
                else -> throw ApplicationException(ErrorCode.EXECUTION_STATUS_UNSUPPORTED)
            }
        } catch (_: IllegalStateException) {
            throw ApplicationException(ErrorCode.EXECUTION_STATUS_TRANSITION_INVALID)
        }
    }

    private fun requireAgentRobot(agentId: UUID, robotId: UUID) {
        agentRepository.findById(agentId)
            ?: throw ApplicationException(ErrorCode.AGENT_NOT_FOUND)
        val robot = robotRepository.findById(robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        if (robot.agentId != agentId) {
            throw ApplicationException(ErrorCode.AGENT_ROBOT_BINDING_MISMATCH)
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
    CANCELLED,
    ;

    fun toExecutionStatus(): ExecutionStatus = when (this) {
        RUNNING -> ExecutionStatus.RUNNING
        COMPLETED -> ExecutionStatus.COMPLETED
        FAILED -> ExecutionStatus.FAILED
        CANCELLED -> ExecutionStatus.CANCELLED
    }
}

data class ExecutionStatusReport(
    val executionId: UUID,
    val robotId: UUID,
    val status: ExecutionStatus,
)

data class ActiveExecutionReport(
    val executionId: UUID,
    val robotId: UUID,
    val status: ExecutionStatus,
)

data class ExecutionRecoveryReport(
    val robotId: UUID,
    val executionId: UUID?,
    val previousStatus: ExecutionStatus?,
    val status: ExecutionStatus?,
    val action: RecoveryAction,
)

enum class RecoveryAction {
    NO_ACTIVE_EXECUTION,
    RECOVERED_AS_FAILED,
}

private fun ExecutionStatus.isTerminal(): Boolean = this == ExecutionStatus.COMPLETED ||
    this == ExecutionStatus.FAILED || this == ExecutionStatus.CANCELLED

private fun ExecutionStatus.isActive(): Boolean = this == ExecutionStatus.ASSIGNED ||
    this == ExecutionStatus.RUNNING

private fun ExecutionStatus.isRecoverable(): Boolean = this == ExecutionStatus.ASSIGNED ||
    this == ExecutionStatus.RUNNING
