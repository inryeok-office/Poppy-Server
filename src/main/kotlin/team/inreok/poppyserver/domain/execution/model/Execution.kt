package team.inreok.poppyserver.domain.execution.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class Execution private constructor(
    val id: UUID,
    statusValue: ExecutionStatus,
    assignedRobotIdValue: UUID?,
    val sessionId: UUID?,
    val blockVersion: Long?,
    val queuedAt: Instant?,
) {

    var status: ExecutionStatus = statusValue
        private set

    var assignedRobotId: UUID? = assignedRobotIdValue
        private set

    fun assign() {
        transitionTo(ExecutionStatus.ASSIGNED)
    }

    fun assignToRobot(robotId: UUID) {
        assign()
        bindRobot(robotId)
    }

    fun bindRobot(robotId: UUID) {
        check(assignedRobotId == null || assignedRobotId == robotId) {
            "Execution이 다른 Robot에 이미 배정되어 있습니다"
        }
        assignedRobotId = robotId
    }

    fun start() {
        transitionTo(ExecutionStatus.RUNNING)
    }

    fun complete() {
        transitionTo(ExecutionStatus.COMPLETED)
    }

    fun fail() {
        transitionTo(ExecutionStatus.FAILED)
    }

    fun cancel() {
        transitionTo(ExecutionStatus.CANCELLED)
    }

    private fun transitionTo(nextStatus: ExecutionStatus) {
        check(nextStatus in allowedTransitions.getValue(status)) {
            "$status 상태에서는 $nextStatus 상태로 전환할 수 없습니다"
        }
        status = nextStatus
    }

    companion object {
        private val allowedTransitions = mapOf(
            ExecutionStatus.QUEUED to setOf(
                ExecutionStatus.ASSIGNED,
                ExecutionStatus.CANCELLED,
            ),
            ExecutionStatus.ASSIGNED to setOf(
                ExecutionStatus.RUNNING,
                ExecutionStatus.FAILED,
                ExecutionStatus.CANCELLED,
            ),
            ExecutionStatus.RUNNING to setOf(
                ExecutionStatus.COMPLETED,
                ExecutionStatus.FAILED,
                ExecutionStatus.CANCELLED,
            ),
            ExecutionStatus.COMPLETED to emptySet(),
            ExecutionStatus.FAILED to emptySet(),
            ExecutionStatus.CANCELLED to emptySet(),
        )

        fun create(): Execution = Execution(
            id = UUID.randomUUID(),
            statusValue = ExecutionStatus.QUEUED,
            assignedRobotIdValue = null,
            sessionId = null,
            blockVersion = null,
            queuedAt = null,
        )

        fun create(
            sessionId: UUID,
            blockVersion: Long,
            queuedAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS),
        ): Execution = Execution(
            id = UUID.randomUUID(),
            statusValue = ExecutionStatus.QUEUED,
            assignedRobotIdValue = null,
            sessionId = sessionId,
            blockVersion = blockVersion,
            queuedAt = queuedAt,
        )

        fun restore(
            id: UUID,
            status: ExecutionStatus,
            assignedRobotId: UUID? = null,
            sessionId: UUID? = null,
            blockVersion: Long? = null,
            queuedAt: Instant? = null,
        ): Execution = Execution(
            id = id,
            statusValue = status,
            assignedRobotIdValue = assignedRobotId,
            sessionId = sessionId,
            blockVersion = blockVersion,
            queuedAt = queuedAt,
        )
    }
}
