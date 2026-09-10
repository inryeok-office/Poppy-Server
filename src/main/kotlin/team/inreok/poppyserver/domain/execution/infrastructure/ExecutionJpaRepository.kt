package team.inreok.poppyserver.domain.execution.infrastructure

import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ExecutionJpaRepository : JpaRepository<ExecutionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from ExecutionEntity execution where execution.id = :id")
    fun findByIdForAllocation(@Param("id") id: UUID): ExecutionEntity?
}
