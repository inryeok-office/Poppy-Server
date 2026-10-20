package team.inreok.poppyserver.domain.agent.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.CapabilitySupportStatus
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnBean(AgentRepository::class, RobotRepository::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AgentCapabilityEvidenceService(
    private val agentRepository: AgentRepository,
    private val robotRepository: RobotRepository,
) {
    @Transactional(readOnly = true)
    fun find(agentId: UUID, robotId: UUID): AgentCapabilityEvidence {
        agentRepository.findById(agentId)
            ?: throw ApplicationException(ErrorCode.AGENT_NOT_FOUND)
        val robot = robotRepository.findById(robotId)
            ?: throw ApplicationException(ErrorCode.ROBOT_NOT_FOUND)
        if (robot.agentId != agentId) {
            throw ApplicationException(ErrorCode.AGENT_CAPABILITY_EVIDENCE_ACCESS_DENIED)
        }
        return AgentCapabilityEvidence(
            robotId = robot.id,
            capabilities = robot.capabilities.values
                .sortedBy { it.code }
                .map { AgentCapabilityEvidenceItem(it.code, it.status) },
        )
    }
}

data class AgentCapabilityEvidence(
    val robotId: UUID,
    val capabilities: List<AgentCapabilityEvidenceItem>,
)

data class AgentCapabilityEvidenceItem(
    val code: String,
    val status: CapabilitySupportStatus,
)
