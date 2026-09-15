package team.inreok.poppyserver.domain.execution.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "spring.datasource",
    name = ["url"],
)
@ConditionalOnProperty(
    prefix = "poppy.execution",
    name = ["offline-recovery-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class OfflineExecutionRecoveryScheduler(
    private val offlineExecutionRecoveryService: OfflineExecutionRecoveryService,
) {
    @Scheduled(fixedDelayString = "\${poppy.execution.offline-recovery-interval-milliseconds:30000}")
    fun recoverOfflineExecutions() {
        offlineExecutionRecoveryService.recoverOfflineExecutions()
    }
}
