package team.inreok.poppyserver.domain.session.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SimulationPass private constructor(
    val sessionId: UUID,
    val blockVersion: Long,
    val passedAt: Instant,
) {
    companion object {
        fun create(sessionId: UUID, blockVersion: Long, passedAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)): SimulationPass =
            SimulationPass(sessionId, blockVersion, passedAt)

        fun restore(sessionId: UUID, blockVersion: Long, passedAt: Instant): SimulationPass =
            SimulationPass(sessionId, blockVersion, passedAt)
    }
}
