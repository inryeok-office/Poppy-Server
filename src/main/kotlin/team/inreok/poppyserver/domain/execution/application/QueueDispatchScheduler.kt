package team.inreok.poppyserver.domain.execution.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@ConditionalOnProperty(
    prefix = "poppy.execution",
    name = ["queue-dispatch-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class QueueDispatchScheduler(
    private val queueDispatchService: QueueDispatchService,
) {
    @Scheduled(fixedDelayString = "\${poppy.execution.queue-dispatch-interval-milliseconds:1000}")
    fun dispatchQueue() {
        queueDispatchService.dispatchAvailable()
    }
}
