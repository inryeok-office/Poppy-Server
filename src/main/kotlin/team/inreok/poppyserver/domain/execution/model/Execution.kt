package team.inreok.poppyserver.domain.execution.model

import java.util.UUID

class Execution private constructor(
    val id: UUID,
    statusValue: ExecutionStatus,
) {

    var status: ExecutionStatus = statusValue
        private set

    fun assign() {
        transitionTo(ExecutionStatus.ASSIGNED)
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
        )
    }
}
