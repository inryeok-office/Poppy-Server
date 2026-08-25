package team.inreok.poppyserver.domain.robot.application

import java.util.UUID
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus

interface RobotRepository {
    fun save(robot: Robot): Robot

    fun findById(id: UUID): Robot?

    fun findAllById(ids: Collection<UUID>): List<Robot>

    fun saveAll(robots: Collection<Robot>): List<Robot>

    fun findAll(
        operationStatus: RobotOperationStatus?,
        connectionStatus: RobotConnectionStatus?,
    ): List<Robot>

    fun existsByAgentId(agentId: UUID): Boolean
}
