package team.inreok.poppyserver.domain.session.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@ConditionalOnProperty(
    prefix = "poppy.session",
    name = ["expiration-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class SessionExpirationScheduler(
    private val sessionExpirationService: SessionExpirationService,
) {
    @Scheduled(fixedDelayString = "\${poppy.session.expiration-scan-interval-milliseconds:60000}")
    fun expireInactiveSessions() {
        sessionExpirationService.expireInactiveSessions()
    }
}
