package team.inreok.poppyserver.domain.agent.application

import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.agent.model.Agent
import team.inreok.poppyserver.domain.robot.application.RobotAgentBinding
import team.inreok.poppyserver.domain.robot.application.RobotHeartbeatCommand
import team.inreok.poppyserver.domain.robot.application.RobotManagementService
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AgentManagementService(
    private val agentRepository: AgentRepository,
    private val robotManagementService: RobotManagementService,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun register(command: RegisterAgentCommand): AgentRegistrationResult {
        validateRegistration(command)
        if (agentRepository.findByName(command.agentName) != null) {
            throw ApplicationException(ErrorCode.AGENT_ALREADY_REGISTERED)
        }

        val agent = Agent.register(
            name = command.agentName,
            agentVersion = command.agentVersion,
            sdkVersion = command.sdkVersion,
            platform = command.platform,
            registeredAt = Instant.now(clock),
        )
        try {
            agentRepository.save(agent)
        } catch (_: DataIntegrityViolationException) {
            throw ApplicationException(ErrorCode.AGENT_ALREADY_REGISTERED)
        }

        val acceptedRobotIds = command.robots.map { robot ->
            robotManagementService.bindAgent(
                id = requireNotNull(robot.robotId),
                agentId = agent.id,
                binding = RobotAgentBinding(
                    model = robot.model,
                    edition = robot.edition,
                    firmwareVersion = robot.firmwareVersion,
                    capabilityCodes = robot.capabilityCodes,
                ),
            ).id
        }
        return AgentRegistrationResult(agent, acceptedRobotIds)
    }

    @Transactional
    fun heartbeat(agentId: UUID, command: HeartbeatCommand): Instant {
        validateHeartbeat(command)
        val agent = agentRepository.findById(agentId)
            ?: throw ApplicationException(ErrorCode.AGENT_NOT_FOUND)

        val acceptedAt = Instant.now(clock)
        agent.recordHeartbeat(acceptedAt)
        agentRepository.save(agent)
        robotManagementService.recordHeartbeats(
            agentId = agentId,
            commands = command.robots.map { robot ->
                RobotHeartbeatCommand(
                    robotId = robot.robotId,
                    at = acceptedAt,
                    connectionStatus = robot.connectionStatus,
                    operationStatus = robot.operationStatus,
                    batteryPercent = robot.batteryPercent,
                    currentExecutionId = robot.currentExecutionId,
                    currentExecutionIdProvided = robot.currentExecutionIdProvided,
                )
            },
        )
        return acceptedAt
    }

    private fun validateRegistration(command: RegisterAgentCommand) {
        if (command.robots.distinctBy { it.robotId }.size != command.robots.size) {
            throw ApplicationException(ErrorCode.AGENT_REGISTRATION_INVALID)
        }
    }

    private fun validateHeartbeat(command: HeartbeatCommand) {
        if (command.robots.distinctBy { it.robotId }.size != command.robots.size) {
            throw ApplicationException(ErrorCode.HEARTBEAT_PAYLOAD_INVALID)
        }
    }
}

data class RegisterAgentCommand(
    val agentName: String,
    val agentVersion: String,
    val sdkVersion: String,
    val platform: String,
    val robots: List<RegisterAgentRobotCommand>,
)

data class RegisterAgentRobotCommand(
    val robotId: UUID?,
    val model: String,
    val edition: String,
    val firmwareVersion: String,
    val capabilityCodes: Set<String>,
)

data class HeartbeatCommand(
    val sentAt: Instant,
    val robots: List<HeartbeatRobotCommand>,
)

data class HeartbeatRobotCommand(
    val robotId: UUID,
    val connectionStatus: RobotConnectionStatus,
    val operationStatus: RobotOperationStatus,
    val batteryPercent: Int?,
    val currentExecutionId: UUID?,
    val currentExecutionIdProvided: Boolean,
)

data class AgentRegistrationResult(
    val agent: Agent,
    val acceptedRobotIds: List<UUID>,
)
