package team.inreok.poppyserver.domain.robot.application

import java.time.Clock
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus

@Service
@ConditionalOnBean(RobotRepository::class, StaleRobotQueryRepository::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class RobotHeartbeatTimeoutService(
    private val staleRobotQueryRepository: StaleRobotQueryRepository,
    private val robotRepository: RobotRepository,
    @Value("\${poppy.agent.heartbeat-timeout-seconds:90}")
    private val heartbeatTimeoutSeconds: Long,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Scheduled(fixedDelayString = "\${poppy.agent.heartbeat-scan-interval-milliseconds:30000}")
    @Transactional
    fun detectStaleRobots() {
        detectStaleRobots(Instant.now(clock))
    }

    @Transactional
    fun detectStaleRobots(now: Instant) {
        require(heartbeatTimeoutSeconds > 0) { "heartbeat timeout은 양수여야 합니다" }
        val cutoff = now.minusSeconds(heartbeatTimeoutSeconds)
        staleRobotQueryRepository.findStaleRobotIds(cutoff).forEach { robotId ->
            val robot = robotRepository.findByIdForStatusUpdate(robotId) ?: return@forEach
            val lastHeartbeatAt = robot.lastHeartbeatAt ?: return@forEach
            if (robot.connectionStatus != RobotConnectionStatus.ONLINE || !lastHeartbeatAt.isBefore(cutoff)) {
                return@forEach
            }
            robot.markOffline()
            robotRepository.save(robot)
        }
    }
}
