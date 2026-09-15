package team.inreok.poppyserver.domain.execution.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(ExecutionAllocationService::class, QueuedExecutionQueryRepository::class)
class QueueDispatchService(
    private val queuedExecutionQueryRepository: QueuedExecutionQueryRepository,
    private val executionAllocationService: ExecutionAllocationService,
) {
    fun dispatchAvailable(): Int {
        var dispatchedCount = 0
        while (true) {
            val executionId = queuedExecutionQueryRepository.findNextQueuedExecutionId() ?: return dispatchedCount
            try {
                if (executionAllocationService.allocate(executionId) == null) {
                    return dispatchedCount
                }
                dispatchedCount++
            } catch (exception: IllegalStateException) {
                if (queuedExecutionQueryRepository.findNextQueuedExecutionId() == executionId) {
                    throw exception
                }
            }
        }
    }
}
