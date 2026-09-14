package team.inreok.poppyserver.domain.session.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface SimulationPassJpaRepository : JpaRepository<SimulationPassEntity, SimulationPassEntityId>
