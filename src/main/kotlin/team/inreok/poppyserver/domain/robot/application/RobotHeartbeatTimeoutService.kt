package team.inreok.poppyserver.domain.robot.application

import java.time.Clock
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(RobotOfflineBatchProcessor::class, StaleRobotQueryRepository::class)
class RobotHeartbeatTimeoutService(
    private val staleRobotQueryRepository: StaleRobotQueryRepository,
    private val robotOfflineBatchProcessor: RobotOfflineBatchProcessor,
    @Value("\${poppy.agent.heartbeat-timeout-seconds:90}")
    private val heartbeatTimeoutSeconds: Long,
    @Value("\${poppy.agent.heartbeat-offline-batch-size:100}")
    private val heartbeatOfflineBatchSize: Int,
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        require(heartbeatTimeoutSeconds > 0) { "heartbeat timeout은 양수여야 합니다" }
        require(heartbeatOfflineBatchSize > 0) { "heartbeat offline batch size는 양수여야 합니다" }
    }

    @Scheduled(fixedDelayString = "\${poppy.agent.heartbeat-scan-interval-milliseconds:30000}")
    fun detectStaleRobots() {
        detectStaleRobots(Instant.now(clock))
    }

    fun detectStaleRobots(now: Instant) {
        val cutoff = now.minusSeconds(heartbeatTimeoutSeconds)
        while (true) {
            val robotIds = staleRobotQueryRepository.findStaleRobotIds(cutoff, heartbeatOfflineBatchSize)
            if (robotIds.isEmpty()) {
                return
            }
            robotOfflineBatchProcessor.markOffline(robotIds, cutoff)
        }
    }
}
