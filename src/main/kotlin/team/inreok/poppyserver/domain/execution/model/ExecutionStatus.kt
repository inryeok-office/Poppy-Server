package team.inreok.poppyserver.domain.execution.model

enum class ExecutionStatus {
    QUEUED,
    ASSIGNED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}
