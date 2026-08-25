package team.inreok.poppyserver.domain.agent.application

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.agent.model.Agent
import team.inreok.poppyserver.domain.robot.application.RobotAgentBinding
import team.inreok.poppyserver.domain.robot.application.RobotHeartbeatCommand
import team.inreok.poppyserver.domain.robot.application.RobotManagementService
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnBean(value = [AgentRepository::class, RobotManagementService::class])
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AgentManagementService(
    private val agentRepository: AgentRepository,
    private val robotManagementService: RobotManagementService,
    @Value("\${poppy.agent.token:}") private val configuredToken: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun register(token: String?, command: RegisterAgentCommand): AgentRegistrationResult {
        validateToken(token)
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
    fun heartbeat(token: String?, agentId: UUID, command: HeartbeatCommand): Instant {
        validateToken(token)
        validateHeartbeat(command)
        val agent = agentRepository.findById(agentId)
            ?: throw ApplicationException(ErrorCode.AGENT_NOT_FOUND)

        agent.recordHeartbeat(command.sentAt)
        agentRepository.save(agent)
        command.robots.forEach { robot ->
            robotManagementService.recordHeartbeat(
                agentId = agentId,
                command = RobotHeartbeatCommand(
                    robotId = robot.robotId,
                    sentAt = command.sentAt,
                    connectionStatus = robot.connectionStatus,
                    operationStatus = robot.operationStatus,
                    batteryPercent = robot.batteryPercent,
                    currentExecutionId = robot.currentExecutionId,
                ),
            )
        }
        return Instant.now(clock)
    }

    private fun validateToken(token: String?) {
        val provided = token?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf()
        val expected = configuredToken.toByteArray(StandardCharsets.UTF_8)
        if (expected.isEmpty() || !MessageDigest.isEqual(provided, expected)) {
            throw ApplicationException(ErrorCode.AGENT_AUTH_INVALID)
        }
    }

    private fun validateRegistration(command: RegisterAgentCommand) {
        if (command.robots.mapNotNull { it.robotId }.size != command.robots.size ||
            command.robots.mapNotNull { it.robotId }.toSet().size != command.robots.size
        ) {
            throw ApplicationException(ErrorCode.AGENT_REGISTRATION_INVALID)
        }
    }

    private fun validateHeartbeat(command: HeartbeatCommand) {
        if (command.robots.map { it.robotId }.toSet().size != command.robots.size ||
            command.robots.any { it.batteryPercent != null && it.batteryPercent !in 0..100 }
        ) {
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
    val connectionStatus: team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus,
    val operationStatus: team.inreok.poppyserver.domain.robot.model.RobotOperationStatus,
    val batteryPercent: Int?,
    val currentExecutionId: UUID?,
)

data class AgentRegistrationResult(
    val agent: Agent,
    val acceptedRobotIds: List<UUID>,
)
