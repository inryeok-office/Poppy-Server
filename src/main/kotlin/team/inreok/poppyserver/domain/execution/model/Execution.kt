package team.inreok.poppyserver.domain.execution.model

import java.util.UUID

class Execution private constructor(
    val id: UUID,
    statusValue: ExecutionStatus,
    assignedRobotIdValue: UUID?,
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
        )

        fun restore(id: UUID, status: ExecutionStatus, assignedRobotId: UUID? = null): Execution = Execution(
            id = id,
            statusValue = status,
            assignedRobotIdValue = assignedRobotId,
        )
    }
}
