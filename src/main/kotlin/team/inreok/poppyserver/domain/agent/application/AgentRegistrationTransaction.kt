package team.inreok.poppyserver.domain.agent.application

import java.time.Clock
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.agent.model.Agent
import team.inreok.poppyserver.domain.robot.application.RobotAgentBinding
import team.inreok.poppyserver.domain.robot.application.RobotManagementService
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AgentRegistrationTransaction(
    private val agentRepository: AgentRepository,
    private val robotManagementService: RobotManagementService,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun register(command: RegisterAgentCommand): AgentRegistrationResult {
        val existingAgent = agentRepository.findByName(command.agentName)
        val agent = existingAgent ?: Agent.register(
            name = command.agentName,
            agentVersion = command.agentVersion,
            sdkVersion = command.sdkVersion,
            platform = command.platform,
            registeredAt = Instant.now(clock),
        )
        existingAgent?.refreshRegistrationMetadata(
            agentVersion = command.agentVersion,
            sdkVersion = command.sdkVersion,
            platform = command.platform,
        )
        val persistedAgent = try {
            agentRepository.save(agent)
        } catch (exception: DataIntegrityViolationException) {
            if (existingAgent == null) {
                throw AgentRegistrationRaceException()
            }
            throw exception
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
        return AgentRegistrationResult(persistedAgent, acceptedRobotIds)
    }
}

class AgentRegistrationRaceException : RuntimeException()
