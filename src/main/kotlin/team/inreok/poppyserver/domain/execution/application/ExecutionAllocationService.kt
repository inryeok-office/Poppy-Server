package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotCapabilityMatcher
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus

@Service
@ConditionalOnBean(ExecutionRepository::class, RobotRepository::class)
class ExecutionAllocationService(
    private val executionRepository: ExecutionRepository,
    private val robotRepository: RobotRepository,
    private val robotCapabilityMatcher: RobotCapabilityMatcher,
    private val executionStatusEventPublisher: ExecutionStatusEventPublisher,
) {
    @Transactional
    fun allocate(executionId: UUID): UUID? {
        val execution = executionRepository.findByIdForAllocation(executionId)
            ?: throw NoSuchElementException("Execution을 찾을 수 없습니다")
        check(execution.status == ExecutionStatus.QUEUED) {
            "QUEUED 상태의 Execution만 배정할 수 있습니다"
        }

        val robot = findAllocatableRobot(execution) ?: return null
        execution.assignToRobot(robot.id)
        robot.assignExecution(execution.id)
        executionRepository.save(execution)
        robotRepository.save(robot)
        execution.sessionId?.let { sessionId ->
            executionStatusEventPublisher.publish(
                ExecutionStatusChangedEvent(
                    executionId = execution.id,
                    sessionId = sessionId,
                    status = execution.status,
                ),
            )
        }
        return robot.id
    }

    private fun findAllocatableRobot(execution: Execution): Robot? {
        if (execution.compiledCommandPayload == null) {
            return robotRepository.findAvailableForAllocation()
        }

        return robotRepository
            .findAll(RobotOperationStatus.READY, RobotConnectionStatus.ONLINE)
            .asSequence()
            .filter { it.active && !it.occupied }
            .sortedBy { it.id }
            .mapNotNull { candidate -> robotRepository.findByIdForStatusUpdate(candidate.id) }
            .firstOrNull { candidate ->
                candidate.active &&
                    candidate.connectionStatus == RobotConnectionStatus.ONLINE &&
                    candidate.operationStatus == RobotOperationStatus.READY &&
                    !candidate.occupied &&
                    robotCapabilityMatcher.matches(candidate.capabilities.values, execution.requiredCapabilities)
            }
    }
}
