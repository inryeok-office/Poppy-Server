package team.inreok.poppyserver.domain.robot.infrastructure

import java.util.UUID
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.CapabilitySupportStatus
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotCapability
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class RobotPersistenceAdapter(
    private val robotJpaRepository: RobotJpaRepository,
    private val entityManager: EntityManager,
) : RobotRepository {
    override fun save(robot: Robot): Robot {
        val existing = robotJpaRepository.findById(robot.id).orElse(null)
        val entity = existing ?: RobotEntity(id = robot.id)
        entity.updateFrom(robot)
        if (existing == null) {
            entityManager.persist(entity)
            entityManager.flush()
        }
        return entity.toDomain()
    }

    override fun findById(id: UUID): Robot? = robotJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAvailableForAllocation(): Robot? =
        robotJpaRepository
            .findFirstByActiveTrueAndConnectionStatusAndOperationStatusAndCurrentExecutionIdIsNullOrderByIdAsc(
                connectionStatus = RobotConnectionStatus.ONLINE,
                operationStatus = RobotOperationStatus.READY,
            )
            ?.toDomain()

    override fun findAllById(ids: Collection<UUID>): List<Robot> =
        robotJpaRepository.findAllById(ids).map { it.toDomain() }

    override fun saveAll(robots: Collection<Robot>): List<Robot> {
        val existing = robotJpaRepository.findAllById(robots.map { it.id }).associateBy { it.id }
        val entities = robots.map { robot ->
            val entity = existing[robot.id] ?: RobotEntity(id = robot.id)
            entity.updateFrom(robot)
            if (robot.id !in existing) {
                entityManager.persist(entity)
            }
            entity
        }
        entityManager.flush()
        return entities.map { it.toDomain() }
    }

    override fun findAll(
        operationStatus: RobotOperationStatus?,
        connectionStatus: RobotConnectionStatus?,
    ): List<Robot> = when {
        operationStatus != null && connectionStatus != null ->
            robotJpaRepository.findAllByOperationStatusAndConnectionStatus(operationStatus, connectionStatus)
        operationStatus != null -> robotJpaRepository.findAllByOperationStatus(operationStatus)
        connectionStatus != null -> robotJpaRepository.findAllByConnectionStatus(connectionStatus)
        else -> robotJpaRepository.findAll()
    }.map { it.toDomain() }

    override fun existsByAgentId(agentId: UUID): Boolean = robotJpaRepository.existsByAgentId(agentId)

    private fun RobotEntity.updateFrom(robot: Robot) {
        id = robot.id
        alias = robot.alias
        model = robot.model
        edition = robot.edition
        firmwareVersion = robot.firmwareVersion
        sdkVersion = robot.sdkVersion
        agentId = robot.agentId
        safetyProfileId = robot.safetyProfileId
        isExternal = robot.isExternal
        active = robot.active
        connectionStatus = robot.connectionStatus
        operationStatus = robot.operationStatus
        currentExecutionId = robot.currentExecutionId
        lastHeartbeatAt = robot.lastHeartbeatAt
        batteryPercent = robot.batteryPercent
        val incoming = robot.capabilities.values.associateBy { it.code }
        capabilities.removeIf { it.code !in incoming }
        capabilities.forEach { capability ->
            capability.supportStatus = incoming.getValue(capability.code).status.name
        }
        val existingCodes = capabilities.map { it.code }.toSet()
        capabilities.addAll(
            incoming.values
                .filter { it.code !in existingCodes }
                .map { capability ->
                    RobotCapabilityEntity(
                        robot = this,
                        code = capability.code,
                        supportStatus = capability.status.name,
                    )
                },
        )
    }

    private fun RobotEntity.toDomain(): Robot = Robot.restore(
        id = requireNotNull(id),
        alias = alias,
        model = model,
        edition = edition,
        firmwareVersion = firmwareVersion,
        sdkVersion = sdkVersion,
        agentId = agentId,
        safetyProfileId = safetyProfileId,
        isExternal = isExternal,
        active = active,
        currentExecutionId = currentExecutionId,
        lastHeartbeatAt = lastHeartbeatAt,
        batteryPercent = batteryPercent,
        connectionStatus = connectionStatus,
        operationStatus = operationStatus,
        capabilities = capabilities.map { capability ->
            RobotCapability(
                code = capability.code,
                status = CapabilitySupportStatus.valueOf(capability.supportStatus),
            )
        },
    )
}
