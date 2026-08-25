package team.inreok.poppyserver.domain.agent.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "agents")
class AgentEntity(
    @Id
    var id: UUID? = null,
    @Column(nullable = false, columnDefinition = "text")
    var name: String = "",
    @Column(name = "agent_version", nullable = false, columnDefinition = "text")
    var agentVersion: String = "",
    @Column(name = "sdk_version", nullable = false, columnDefinition = "text")
    var sdkVersion: String = "",
    @Column(nullable = false, columnDefinition = "text")
    var platform: String = "",
    @Column(name = "registered_at", nullable = false)
    var registeredAt: Instant = Instant.EPOCH,
    @Column(name = "last_heartbeat_at")
    var lastHeartbeatAt: Instant? = null,
)
