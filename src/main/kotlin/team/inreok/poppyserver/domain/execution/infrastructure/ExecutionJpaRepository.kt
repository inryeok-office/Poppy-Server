package team.inreok.poppyserver.domain.execution.infrastructure

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ExecutionJpaRepository : JpaRepository<ExecutionEntity, UUID>
