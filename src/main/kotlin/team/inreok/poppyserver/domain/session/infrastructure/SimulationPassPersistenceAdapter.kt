package team.inreok.poppyserver.domain.session.infrastructure

import jakarta.persistence.EntityManager
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import team.inreok.poppyserver.domain.session.application.SimulationPassRepository
import team.inreok.poppyserver.domain.session.model.SimulationPass

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SimulationPassPersistenceAdapter(
    private val simulationPassJpaRepository: SimulationPassJpaRepository,
    private val entityManager: EntityManager,
) : SimulationPassRepository {
    override fun save(pass: SimulationPass): SimulationPass {
        val id = SimulationPassEntityId(pass.sessionId, pass.blockVersion)
        val existing = simulationPassJpaRepository.findById(id).orElse(null)
        check(existing == null) { "SimulationPass is immutable" }
        val entity = SimulationPassEntity(
            sessionId = pass.sessionId,
            blockVersion = pass.blockVersion,
            passedAt = pass.passedAt,
        )
        entityManager.persist(entity)
        entityManager.flush()
        return entity.toDomain()
    }

    override fun findById(sessionId: UUID, blockVersion: Long): SimulationPass? =
        simulationPassJpaRepository.findById(SimulationPassEntityId(sessionId, blockVersion)).orElse(null)?.toDomain()

    private fun SimulationPassEntity.toDomain(): SimulationPass = SimulationPass.restore(
        sessionId = requireNotNull(sessionId),
        blockVersion = blockVersion,
        passedAt = passedAt,
    )
}
