package team.inreok.poppyserver.domain.agent.model

import java.time.Instant
import java.util.UUID

class Agent private constructor(
    val id: UUID,
    nameValue: String,
    agentVersionValue: String,
    sdkVersionValue: String,
    platformValue: String,
    registeredAtValue: Instant,
    lastHeartbeatAtValue: Instant?,
    credentialDigestValue: String?,
) {
    val name: String = requireNotBlank(nameValue, "agentName")
    var agentVersion: String = requireNotBlank(agentVersionValue, "agentVersion")
        private set
    var sdkVersion: String = requireNotBlank(sdkVersionValue, "sdkVersion")
        private set
    var platform: String = requireNotBlank(platformValue, "platform")
        private set
    val registeredAt: Instant = registeredAtValue

    var lastHeartbeatAt: Instant? = lastHeartbeatAtValue
        private set

    private var credentialDigestValue: String? = credentialDigestValue

    val credentialDigest: String?
        get() = credentialDigestValue

    fun recordHeartbeat(at: Instant) {
        lastHeartbeatAt = at
    }

    fun refreshRegistrationMetadata(
        agentVersion: String,
        sdkVersion: String,
        platform: String,
    ) {
        this.agentVersion = requireNotBlank(agentVersion, "agentVersion")
        this.sdkVersion = requireNotBlank(sdkVersion, "sdkVersion")
        this.platform = requireNotBlank(platform, "platform")
    }

    fun rotateCredential(digest: String) {
        credentialDigestValue = requireNotBlank(digest, "credentialDigest")
    }

    companion object {
        fun register(
            name: String,
            agentVersion: String,
            sdkVersion: String,
            platform: String,
            registeredAt: Instant,
            credentialDigest: String? = null,
        ): Agent = Agent(
            id = UUID.randomUUID(),
            nameValue = name,
            agentVersionValue = agentVersion,
            sdkVersionValue = sdkVersion,
            platformValue = platform,
            registeredAtValue = registeredAt,
            lastHeartbeatAtValue = null,
            credentialDigestValue = credentialDigest,
        )

        fun restore(
            id: UUID,
            name: String,
            agentVersion: String,
            sdkVersion: String,
            platform: String,
            registeredAt: Instant,
            lastHeartbeatAt: Instant?,
            credentialDigest: String? = null,
        ): Agent = Agent(
            id = id,
            nameValue = name,
            agentVersionValue = agentVersion,
            sdkVersionValue = sdkVersion,
            platformValue = platform,
            registeredAtValue = registeredAt,
            lastHeartbeatAtValue = lastHeartbeatAt,
            credentialDigestValue = credentialDigest,
        )

        private fun requireNotBlank(value: String, field: String): String {
            require(value.isNotBlank()) { "$field 는 비어 있을 수 없습니다" }
            return value
        }
    }
}
