package team.inreok.poppyserver.domain.robot.infrastructure

import java.time.Instant
import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus

interface RobotJpaRepository : JpaRepository<RobotEntity, UUID> {
    fun existsByAgentId(agentId: UUID): Boolean

    @Query(
        "select robot.id from RobotEntity robot " +
            "where robot.connectionStatus = :connectionStatus " +
            "and robot.lastHeartbeatAt < :before " +
            "order by robot.id",
    )
    fun findStaleRobotIds(
        @Param("connectionStatus") connectionStatus: RobotConnectionStatus,
        @Param("before") before: Instant,
        pageable: Pageable,
    ): List<UUID>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select robot from RobotEntity robot where robot.id = :id")
    fun findByIdForStatusUpdate(@Param("id") id: UUID): RobotEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByActiveTrueAndConnectionStatusAndOperationStatusAndCurrentExecutionIdIsNullOrderByIdAsc(
        connectionStatus: RobotConnectionStatus,
        operationStatus: RobotOperationStatus,
    ): RobotEntity?

    @Query(
        "select robot.id from RobotEntity robot " +
            "where robot.active = true " +
            "and robot.connectionStatus = :connectionStatus " +
            "and robot.operationStatus = :operationStatus " +
            "and robot.currentExecutionId is null " +
            "order by robot.id",
    )
    fun findAvailableForAllocationCandidateIds(
        @Param("connectionStatus") connectionStatus: RobotConnectionStatus,
        @Param("operationStatus") operationStatus: RobotOperationStatus,
    ): List<UUID>

    fun findAllByOperationStatus(operationStatus: RobotOperationStatus): List<RobotEntity>

    fun findAllByConnectionStatus(connectionStatus: RobotConnectionStatus): List<RobotEntity>

    fun findAllByOperationStatusAndConnectionStatus(
        operationStatus: RobotOperationStatus,
        connectionStatus: RobotConnectionStatus,
    ): List<RobotEntity>
}
