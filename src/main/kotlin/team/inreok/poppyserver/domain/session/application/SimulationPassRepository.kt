package team.inreok.poppyserver.domain.session.application

import java.util.UUID
import team.inreok.poppyserver.domain.session.model.SimulationPass

interface SimulationPassRepository {
    fun save(pass: SimulationPass): SimulationPass
    fun findById(sessionId: UUID, blockVersion: Long): SimulationPass?
}
