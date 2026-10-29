package team.inreok.poppyserver.domain.execution.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class QueueDispatchService(
    private val queuedExecutionQueryRepository: QueuedExecutionQueryRepository,
    private val executionAllocationService: ExecutionAllocationService,
) {
    fun dispatchAvailable(): Int {
        var dispatchedCount = 0
        for (queued in queuedExecutionQueryRepository.findQueuedExecutions()) {
            val allocated = try {
                executionAllocationService.allocate(queued.executionId) != null
            } catch (exception: IllegalStateException) {
                if (queuedExecutionQueryRepository.findQueuedExecutions().any { it.executionId == queued.executionId }) {
                    throw exception
                }
                continue
            }
            if (allocated) {
                dispatchedCount++
            } else if (queued.requestedRobotId == null) {
                return dispatchedCount
            }
        }
        return dispatchedCount
    }
}
