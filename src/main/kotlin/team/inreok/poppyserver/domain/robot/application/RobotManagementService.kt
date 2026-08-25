package team.inreok.poppyserver.domain.robot.application

import java.time.Instant
import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
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

    @Transactional
    fun bindAgent(id: UUID, agentId: UUID, binding: RobotAgentBinding): Robot {
        val robot = robotRepository.findById(id)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)

        if (robot.agentId != null && robot.agentId != agentId) {
            throw ApplicationException(ErrorCode.ROBOT_ALREADY_REGISTERED)
        }
        if (robot.model != binding.model ||
            robot.edition != binding.edition ||
            (robot.firmwareVersion != null && robot.firmwareVersion != binding.firmwareVersion) ||
            !binding.capabilityCodes.containsAll(robot.capabilities.keys)
        ) {
            throw ApplicationException(ErrorCode.AGENT_COMPATIBILITY_INVALID)
        }

        robot.bindToAgent(agentId)
        robot.applyAgentBinding(binding.firmwareVersion, binding.capabilityCodes)
        return try {
            robotRepository.save(robot)
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw ApplicationException(ErrorCode.ROBOT_ALREADY_REGISTERED)
        }
    }

    @Transactional
    fun recordHeartbeats(agentId: UUID, commands: Collection<RobotHeartbeatCommand>): List<Robot> {
        val robots = robotRepository.findAllById(commands.map { it.robotId }).associateBy { it.id }
        if (robots.size != commands.size) {
            throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        }
        commands.forEach { command ->
            val robot = robots.getValue(command.robotId)
            if (robot.agentId != agentId) {
                throw ApplicationException(ErrorCode.AGENT_ROBOT_BINDING_MISMATCH)
            }
            robot.applyHeartbeat(
                at = command.at,
                connectionStatus = command.connectionStatus,
                operationStatus = command.operationStatus,
                batteryPercent = command.batteryPercent,
                currentExecutionId = if (command.currentExecutionIdProvided) {
                    command.currentExecutionId
                } else {
                    robot.currentExecutionId
                },
            )
        }
        return try {
            robotRepository.saveAll(robots.values)
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw ApplicationException(ErrorCode.AGENT_ROBOT_BINDING_MISMATCH)
        }
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

data class RobotAgentBinding(
    val model: String,
    val edition: String,
    val firmwareVersion: String,
    val capabilityCodes: Set<String>,
)

data class RobotHeartbeatCommand(
    val robotId: UUID,
    val at: Instant,
    val connectionStatus: RobotConnectionStatus,
    val operationStatus: RobotOperationStatus,
    val batteryPercent: Int?,
    val currentExecutionId: UUID?,
    val currentExecutionIdProvided: Boolean,
)
