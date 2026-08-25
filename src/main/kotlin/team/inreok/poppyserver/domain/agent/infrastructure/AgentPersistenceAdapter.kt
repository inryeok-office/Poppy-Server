package team.inreok.poppyserver.domain.agent.infrastructure

import jakarta.persistence.EntityManager
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import team.inreok.poppyserver.domain.agent.application.AgentRepository
import team.inreok.poppyserver.domain.agent.model.Agent

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AgentPersistenceAdapter(
    private val agentJpaRepository: AgentJpaRepository,
    private val entityManager: EntityManager,
) : AgentRepository {
    override fun save(agent: Agent): Agent {
        val existing = agentJpaRepository.findById(agent.id).orElse(null)
        val entity = existing ?: AgentEntity(id = agent.id)
        entity.updateFrom(agent)
        if (existing == null) {
            entityManager.persist(entity)
        }
        entityManager.flush()
        return entity.toDomain()
    }

    override fun findById(id: UUID): Agent? = agentJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByName(name: String): Agent? = agentJpaRepository.findByName(name)?.toDomain()

    private fun AgentEntity.updateFrom(agent: Agent) {
        id = agent.id
        name = agent.name
        agentVersion = agent.agentVersion
        sdkVersion = agent.sdkVersion
        platform = agent.platform
        registeredAt = agent.registeredAt
        lastHeartbeatAt = agent.lastHeartbeatAt
    }

    private fun AgentEntity.toDomain(): Agent = Agent.restore(
        id = requireNotNull(id),
        name = name,
        agentVersion = agentVersion,
        sdkVersion = sdkVersion,
        platform = platform,
        registeredAt = registeredAt,
        lastHeartbeatAt = lastHeartbeatAt,
    )
}
