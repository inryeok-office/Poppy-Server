package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.session.application.SessionAccessVerifier
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnBean(ExecutionRepository::class, RobotRepository::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ExecutionCancellationService(
    private val executionRepository: ExecutionRepository,
    private val robotRepository: RobotRepository,
    private val sessionAccessVerifier: SessionAccessVerifier,
    private val executionStatusEventPublisher: ExecutionStatusEventPublisher,
) {
    @Transactional
    fun cancelForSession(executionId: UUID, sessionToken: String?): ExecutionCancellationResult {
        val execution = executionRepository.findById(executionId)
            ?: throw ApplicationException(ErrorCode.EXECUTION_NOT_FOUND)
        val sessionId = execution.sessionId
            ?: throw ApplicationException(ErrorCode.EXECUTION_ACCESS_DENIED)
        sessionAccessVerifier.verifyOwnership(sessionId, sessionToken)
        return cancel(executionId)
    }

    @Transactional
    fun cancel(executionId: UUID): ExecutionCancellationResult {
        val execution = executionRepository.findByIdForStatusUpdate(executionId)
            ?: throw ApplicationException(ErrorCode.EXECUTION_NOT_FOUND)

        return when (execution.status) {
            ExecutionStatus.CANCELLED -> execution.toCancellationResult()
            ExecutionStatus.QUEUED -> cancelQueued(execution)
            ExecutionStatus.ASSIGNED -> cancelAssigned(execution)
            ExecutionStatus.RUNNING -> cancelAssigned(execution)
            ExecutionStatus.COMPLETED,
            ExecutionStatus.FAILED,
            -> throw ApplicationException(ErrorCode.EXECUTION_CANCELLATION_NOT_ALLOWED)
        }
    }

    private fun cancelQueued(execution: Execution): ExecutionCancellationResult {
        execution.cancel()
        executionRepository.save(execution)
        publishStatusChanged(execution)
        return execution.toCancellationResult()
    }

    private fun cancelAssigned(execution: Execution): ExecutionCancellationResult {
        val robotId = execution.assignedRobotId
            ?: throw ApplicationException(ErrorCode.EXECUTION_CANCELLATION_INVARIANT_VIOLATED)
        val robot = robotRepository.findByIdForStatusUpdate(robotId)
            ?: throw ApplicationException(ErrorCode.EXECUTION_CANCELLATION_INVARIANT_VIOLATED)
        if (robot.currentExecutionId != execution.id) {
            throw ApplicationException(ErrorCode.EXECUTION_ROBOT_MISMATCH)
        }

        execution.cancel()
        robot.releaseExecution(execution.id)
        executionRepository.save(execution)
        robotRepository.save(robot)
        publishStatusChanged(execution)
        return execution.toCancellationResult()
    }

    private fun publishStatusChanged(execution: Execution) {
        execution.sessionId?.let { sessionId ->
            executionStatusEventPublisher.publish(
                ExecutionStatusChangedEvent(execution.id, sessionId, execution.status),
            )
        }
    }

    private fun Execution.toCancellationResult(): ExecutionCancellationResult =
        ExecutionCancellationResult(
            executionId = id,
            status = status,
        )
}

data class ExecutionCancellationResult(
    val executionId: UUID,
    val status: ExecutionStatus,
)
