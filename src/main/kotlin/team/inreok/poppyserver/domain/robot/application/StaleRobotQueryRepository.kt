package team.inreok.poppyserver.domain.robot.application

import java.time.Instant
import java.util.UUID

interface StaleRobotQueryRepository {
    fun findStaleRobotIds(before: Instant, limit: Int): List<UUID>
}
