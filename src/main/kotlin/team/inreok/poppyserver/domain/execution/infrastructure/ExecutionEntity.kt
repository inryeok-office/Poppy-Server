package team.inreok.poppyserver.domain.execution.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

@Entity
@Table(name = "executions")
class ExecutionEntity(
    @Id
    var id: UUID? = null,
    @Column(nullable = false, columnDefinition = "text")
    @Enumerated(EnumType.STRING)
    var status: ExecutionStatus = ExecutionStatus.QUEUED,
    @Column(name = "assigned_robot_id")
    var assignedRobotId: UUID? = null,
    @Column(name = "session_id")
    var sessionId: UUID? = null,
    @Column(name = "block_version")
    var blockVersion: Long? = null,
    @Column(name = "compiled_command_payload", columnDefinition = "text")
    var compiledCommandPayload: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_capabilities", columnDefinition = "jsonb")
    var requiredCapabilities: List<String>? = null,
    @Column(name = "queued_at")
    var queuedAt: Instant? = null,
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "finished_at")
    var finishedAt: Instant? = null,
)
