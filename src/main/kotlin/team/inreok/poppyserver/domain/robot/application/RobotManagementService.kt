package team.inreok.poppyserver.domain.robot.application

import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotCapability
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnBean(RobotRepository::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class RobotManagementService(
    private val robotRepository: RobotRepository,
) {
    @Transactional
    fun register(command: RegisterRobotCommand): Robot {
        command.agentId?.let { agentId ->
            if (robotRepository.existsByAgentId(agentId)) {
                throw ApplicationException(ErrorCode.ROBOT_ALREADY_REGISTERED)
            }
        }

        val robot = Robot.register(
            alias = command.alias,
            model = command.model,
            edition = command.edition,
            firmwareVersion = command.firmwareVersion,
            sdkVersion = command.sdkVersion,
            agentId = command.agentId,
            safetyProfileId = command.safetyProfileId,
            isExternal = command.isExternal,
            capabilities = command.capabilities,
        )

        return try {
            robotRepository.save(robot)
        } catch (_: DataIntegrityViolationException) {
            throw ApplicationException(ErrorCode.ROBOT_ALREADY_REGISTERED)
        }
    }

    @Transactional(readOnly = true)
    fun findAll(operationStatus: RobotOperationStatus?, connectionStatus: RobotConnectionStatus?): List<Robot> =
        robotRepository.findAll(operationStatus, connectionStatus)

    @Transactional
    fun update(id: UUID, command: UpdateRobotCommand): Robot {
        val robot = robotRepository.findById(id)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)

        if (robot.occupied && command.operationStatus != null && command.operationStatus != robot.operationStatus) {
            throw ApplicationException(ErrorCode.ROBOT_UPDATE_CONFLICT)
        }

        robot.update(
            alias = command.alias,
            firmwareVersion = command.firmwareVersion,
            sdkVersion = command.sdkVersion,
            capabilities = command.capabilities,
            safetyProfileId = command.safetyProfileId,
            operationStatus = command.operationStatus,
        )
        return robotRepository.save(robot)
    }
}

data class RegisterRobotCommand(
    val alias: String,
    val model: String,
    val edition: String,
    val firmwareVersion: String?,
    val sdkVersion: String?,
    val agentId: UUID?,
    val capabilities: List<RobotCapability>,
    val safetyProfileId: UUID?,
    val isExternal: Boolean,
)

data class UpdateRobotCommand(
    val alias: String?,
    val firmwareVersion: String?,
    val sdkVersion: String?,
    val capabilities: List<RobotCapability>?,
    val safetyProfileId: UUID?,
    val operationStatus: RobotOperationStatus?,
)
