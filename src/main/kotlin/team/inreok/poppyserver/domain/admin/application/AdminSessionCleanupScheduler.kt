package team.inreok.poppyserver.domain.admin.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AdminSessionCleanupScheduler(
    private val adminSessionService: AdminSessionService,
) {
    @Scheduled(fixedDelayString = "\${poppy.admin.session.cleanup-interval-milliseconds:60000}")
    fun deleteInactiveSessions() {
        adminSessionService.deleteInactive()
    }
}
