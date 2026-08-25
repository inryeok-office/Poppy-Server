package team.inreok.poppyserver.domain.agent.application

import java.util.UUID
import team.inreok.poppyserver.domain.agent.model.Agent

interface AgentRepository {
    fun save(agent: Agent): Agent

    fun findById(id: UUID): Agent?

    fun findByName(name: String): Agent?
}
