package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository

@Service
@ConditionalOnBean(ExecutionRepository::class, RobotRepository::class)
class ExecutionAllocationService(
    private val executionRepository: ExecutionRepository,
    private val robotRepository: RobotRepository,
    private val executionStatusEventPublisher: ExecutionStatusEventPublisher? = null,
) {
    @Transactional
    fun allocate(executionId: UUID): UUID? {
        val execution = executionRepository.findByIdForAllocation(executionId)
            ?: throw NoSuchElementException("Execution을 찾을 수 없습니다")
        check(execution.status == ExecutionStatus.QUEUED) {
            "QUEUED 상태의 Execution만 배정할 수 있습니다"
        }

        val robot = robotRepository.findAvailableForAllocation() ?: return null
        execution.assignToRobot(robot.id)
        robot.assignExecution(execution.id)
        executionRepository.save(execution)
        robotRepository.save(robot)
        execution.sessionId?.let { sessionId ->
            executionStatusEventPublisher?.publish(
                ExecutionStatusChangedEvent(
                    executionId = execution.id,
                    sessionId = sessionId,
                    status = execution.status,
                ),
            )
        }
        return robot.id
    }
}
