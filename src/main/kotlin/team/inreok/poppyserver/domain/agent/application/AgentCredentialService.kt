package team.inreok.poppyserver.domain.agent.application

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import team.inreok.poppyserver.global.security.AgentPrincipalResolver

@Service
@ConditionalOnBean(AgentRepository::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AgentCredentialService(
    private val agentRepository: AgentRepository,
) : AgentPrincipalResolver {
    override fun resolveAgentId(credential: String): UUID? = agentRepository
        .findByCredentialDigest(digest(credential))
        ?.id

    fun issue(): IssuedAgentCredential {
        val bytes = ByteArray(CREDENTIAL_BYTES)
        secureRandom.nextBytes(bytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedAgentCredential(raw, digest(raw))
    }

    companion object {
        private const val CREDENTIAL_BYTES = 32
        private val secureRandom = SecureRandom()

        fun digest(credential: String): String = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(credential.toByteArray(Charsets.UTF_8)),
        )
    }
}

data class IssuedAgentCredential(
    val raw: String,
    val digest: String,
)
