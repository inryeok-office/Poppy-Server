package team.inreok.poppyserver.domain.session.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "simulation_passes")
@IdClass(SimulationPassEntityId::class)
class SimulationPassEntity(
    @Id
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID? = null,
    @Id
    @Column(name = "block_version", nullable = false)
    var blockVersion: Long = 0,
    @Column(name = "passed_at", nullable = false)
    var passedAt: Instant = Instant.EPOCH,
)

data class SimulationPassEntityId(
    var sessionId: UUID? = null,
    var blockVersion: Long = 0,
) : Serializable
