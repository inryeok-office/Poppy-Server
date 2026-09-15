package team.inreok.poppyserver.domain.execution.application

import java.util.UUID

interface QueuedExecutionQueryRepository {
    fun findNextQueuedExecutionId(): UUID?
}
