package team.inreok.poppyserver.domain.execution.infrastructure

import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

interface ExecutionJpaRepository : JpaRepository<ExecutionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from ExecutionEntity execution where execution.id = :id")
    fun findByIdForAllocation(@Param("id") id: UUID): ExecutionEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from ExecutionEntity execution where execution.id = :id")
    fun findByIdForStatusUpdate(@Param("id") id: UUID): ExecutionEntity?

    @Query(
        "select execution from ExecutionEntity execution " +
            "where execution.sessionId = :sessionId " +
            "and execution.status in :statuses " +
            "order by execution.queuedAt, execution.id",
    )
    fun findActiveBySessionId(
        @Param("sessionId") sessionId: UUID,
        @Param("statuses") statuses: Set<ExecutionStatus> = ACTIVE_STATUSES,
    ): ExecutionEntity?

    companion object {
        private val ACTIVE_STATUSES = setOf(
            ExecutionStatus.QUEUED,
            ExecutionStatus.ASSIGNED,
            ExecutionStatus.RUNNING,
        )
    }
}
