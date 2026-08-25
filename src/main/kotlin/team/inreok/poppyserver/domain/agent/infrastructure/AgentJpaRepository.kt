package team.inreok.poppyserver.domain.agent.infrastructure

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface AgentJpaRepository : JpaRepository<AgentEntity, UUID> {
    fun findByName(name: String): AgentEntity?
}
