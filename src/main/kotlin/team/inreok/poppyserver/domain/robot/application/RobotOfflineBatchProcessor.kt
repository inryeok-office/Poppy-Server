package team.inreok.poppyserver.domain.robot.application

import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus

@Service
@ConditionalOnBean(RobotRepository::class)
class RobotOfflineBatchProcessor(
    private val robotRepository: RobotRepository,
) {
    @Transactional
    fun markOffline(robotIds: Collection<UUID>, cutoff: Instant) {
        robotIds.forEach { robotId ->
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
