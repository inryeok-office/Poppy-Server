package team.inreok.poppyserver.domain.execution.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class OfflineExecutionRecoveryService(
    private val offlineExecutionRecoveryQueryRepository: OfflineExecutionRecoveryQueryRepository,
    private val executionRepository: ExecutionRepository,
    private val robotRepository: RobotRepository,
    @Value("\${poppy.execution.offline-recovery-batch-size:100}")
    private val batchSize: Int,
) {
    init {
        require(batchSize > 0) { "offline recovery batch size는 양수여야 합니다" }
    }

    @Transactional
    fun recoverOfflineExecutions(): Int = offlineExecutionRecoveryQueryRepository
        .findCandidates(batchSize)
        .count(::recover)

    private fun recover(candidate: OfflineExecutionRecoveryCandidate): Boolean {
        val execution = executionRepository.findByIdForStatusUpdate(candidate.executionId)
            ?: return false
        val robot = robotRepository.findByIdForStatusUpdate(candidate.robotId)
            ?: return false

        if (robot.connectionStatus != RobotConnectionStatus.OFFLINE ||
            robot.currentExecutionId != candidate.executionId
        ) {
            return false
        }
        if (execution.assignedRobotId != robot.id) {
            logger.error(
                "Offline Execution recovery invariant violated: Robot {} and Execution {} are not bound",
                robot.id,
                execution.id,
            )
            return false
        }

        return when (execution.status) {
            ExecutionStatus.ASSIGNED,
            ExecutionStatus.RUNNING,
            -> {
                execution.fail()
                robot.releaseExecution(execution.id)
                executionRepository.save(execution)
                robotRepository.save(robot)
                true
            }

            ExecutionStatus.COMPLETED,
            ExecutionStatus.FAILED,
            ExecutionStatus.CANCELLED,
            ExecutionStatus.QUEUED,
            -> {
                logger.error(
                    "Offline Execution recovery invariant violated: Robot {} points to Execution {} in {} status",
                    robot.id,
                    execution.id,
                    execution.status,
                )
                false
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OfflineExecutionRecoveryService::class.java)
    }
}
