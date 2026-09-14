package team.inreok.poppyserver.domain.execution.infrastructure

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import team.inreok.poppyserver.domain.execution.application.ExecutionDeliveryAssignment
import team.inreok.poppyserver.domain.execution.application.ExecutionDeliveryQueryRepository
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ExecutionDeliveryQueryPersistenceAdapter(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : ExecutionDeliveryQueryRepository {
    override fun findByRobotId(robotId: UUID): ExecutionDeliveryAssignment? = jdbcTemplate
        .query(
            """
            select
                robot.id as robot_id,
                robot.agent_id,
                robot.current_execution_id,
                execution.id as execution_id,
                execution.status as execution_status
            from robots robot
            left join executions execution on execution.id = robot.current_execution_id
            where robot.id = :robotId
            """.trimIndent(),
            mapOf("robotId" to robotId),
        ) { resultSet, _ ->
            ExecutionDeliveryAssignment(
                robotId = resultSet.getObject("robot_id", UUID::class.java),
                agentId = resultSet.getObject("agent_id", UUID::class.java),
                currentExecutionId = resultSet.getObject("current_execution_id", UUID::class.java),
                executionId = resultSet.getObject("execution_id", UUID::class.java),
                executionStatus = resultSet.getString("execution_status")?.let(ExecutionStatus::valueOf),
            )
        }
        .singleOrNull()
}
