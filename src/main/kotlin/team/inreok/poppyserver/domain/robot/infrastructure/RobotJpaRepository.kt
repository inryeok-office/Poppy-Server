package team.inreok.poppyserver.domain.robot.infrastructure

import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus

interface RobotJpaRepository : JpaRepository<RobotEntity, UUID> {
    fun existsByAgentId(agentId: UUID): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByActiveTrueAndConnectionStatusAndOperationStatusAndCurrentExecutionIdIsNullOrderByIdAsc(
        connectionStatus: RobotConnectionStatus,
        operationStatus: RobotOperationStatus,
    ): RobotEntity?

    fun findAllByOperationStatus(operationStatus: RobotOperationStatus): List<RobotEntity>

    fun findAllByConnectionStatus(connectionStatus: RobotConnectionStatus): List<RobotEntity>

    fun findAllByOperationStatusAndConnectionStatus(
        operationStatus: RobotOperationStatus,
        connectionStatus: RobotConnectionStatus,
    ): List<RobotEntity>
}
