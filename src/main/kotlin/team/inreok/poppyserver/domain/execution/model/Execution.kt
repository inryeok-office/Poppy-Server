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
    private var startedAtValue: Instant?,
    private var finishedAtValue: Instant?,
) {

    var status: ExecutionStatus = statusValue
        private set

    var assignedRobotId: UUID? = assignedRobotIdValue
        private set

    val startedAt: Instant?
        get() = startedAtValue

    val finishedAt: Instant?
        get() = finishedAtValue

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

    fun start(at: Instant = now()) {
        transitionTo(ExecutionStatus.RUNNING)
        if (startedAt == null) {
            startedAtValue = at.truncatedTo(ChronoUnit.MICROS)
        }
    }

    fun complete(at: Instant = now()) {
        transitionTo(ExecutionStatus.COMPLETED)
        finish(at)
    }

    fun fail(at: Instant = now()) {
        transitionTo(ExecutionStatus.FAILED)
        finish(at)
    }

    fun cancel(at: Instant = now()) {
        transitionTo(ExecutionStatus.CANCELLED)
        finish(at)
    }

    private fun finish(at: Instant) {
        if (finishedAt == null) {
            finishedAtValue = at.truncatedTo(ChronoUnit.MICROS)
        }
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
            startedAtValue = null,
            finishedAtValue = null,
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
            startedAtValue = null,
            finishedAtValue = null,
        )

        fun restore(
            id: UUID,
            status: ExecutionStatus,
            assignedRobotId: UUID? = null,
            sessionId: UUID? = null,
            blockVersion: Long? = null,
            queuedAt: Instant? = null,
            startedAt: Instant? = null,
            finishedAt: Instant? = null,
        ): Execution = Execution(
            id = id,
            statusValue = status,
            assignedRobotIdValue = assignedRobotId,
            sessionId = sessionId,
            blockVersion = blockVersion,
            queuedAt = queuedAt,
            startedAtValue = startedAt,
            finishedAtValue = finishedAt,
        )

        private fun now(): Instant = Instant.now()
    }
}
