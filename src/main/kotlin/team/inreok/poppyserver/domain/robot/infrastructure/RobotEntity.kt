package team.inreok.poppyserver.domain.robot.infrastructure

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus

@Entity
@Table(name = "robots")
class RobotEntity(
    @Id
    var id: UUID? = null,
    @Column(nullable = false, columnDefinition = "text")
    var alias: String = "",
    @Column(nullable = false, columnDefinition = "text")
    var model: String = "",
    @Column(nullable = false, columnDefinition = "text")
    var edition: String = "",
    @Column(name = "firmware_version", columnDefinition = "text")
    var firmwareVersion: String? = null,
    @Column(name = "sdk_version", columnDefinition = "text")
    var sdkVersion: String? = null,
    @Column(name = "agent_id")
    var agentId: UUID? = null,
    @Column(name = "safety_profile_id")
    var safetyProfileId: UUID? = null,
    @Column(name = "is_external", nullable = false)
    var isExternal: Boolean = false,
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(name = "connection_status", nullable = false, columnDefinition = "text")
    @Enumerated(EnumType.STRING)
    var connectionStatus: RobotConnectionStatus = RobotConnectionStatus.OFFLINE,
    @Column(name = "operational_status", nullable = false, columnDefinition = "text")
    @Enumerated(EnumType.STRING)
    var operationStatus: RobotOperationStatus = RobotOperationStatus.UNAVAILABLE,
    @Column(name = "current_execution_id")
    var currentExecutionId: UUID? = null,
    @Column(name = "last_heartbeat_at")
    var lastHeartbeatAt: Instant? = null,
    @Column(name = "battery_percent")
    var batteryPercent: Int? = null,
    @OneToMany(mappedBy = "robot", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var capabilities: MutableList<RobotCapabilityEntity> = mutableListOf(),
)

@Entity
@Table(name = "robot_capabilities")
class RobotCapabilityEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_id", nullable = false)
    var robot: RobotEntity? = null,
    @Column(nullable = false, columnDefinition = "text")
    var code: String = "",
    @Column(nullable = false, name = "support_status", columnDefinition = "text")
    var supportStatus: String = "UNVERIFIED",
)
