package team.inreok.poppyserver.domain.session.infrastructure

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SimulationPassJpaRepository : JpaRepository<SimulationPassEntity, SimulationPassEntityId> {
    @Modifying(flushAutomatically = true)
    @Query("delete from SimulationPassEntity pass where pass.sessionId = :sessionId")
    fun deleteAllBySessionId(@Param("sessionId") sessionId: UUID)
}
