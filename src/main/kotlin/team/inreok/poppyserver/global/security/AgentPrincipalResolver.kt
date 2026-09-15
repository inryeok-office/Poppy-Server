package team.inreok.poppyserver.global.security

import java.util.UUID

interface AgentPrincipalResolver {
    fun resolveAgentId(credential: String): UUID?
}
