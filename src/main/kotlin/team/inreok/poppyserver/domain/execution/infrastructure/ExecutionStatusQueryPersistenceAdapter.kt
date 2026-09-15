package team.inreok.poppyserver.domain.execution.infrastructure

import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.application.ExecutionStatusQueryRepository
import team.inreok.poppyserver.domain.execution.application.ExecutionStatusView
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ExecutionStatusQueryPersistenceAdapter(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : ExecutionStatusQueryRepository {
    @Transactional(readOnly = true)
    override fun findById(executionId: UUID): ExecutionStatusView? = query(
        whereClause = "where execution.id = :executionId",
        parameters = mapOf("executionId" to executionId),
    ).singleOrNull()

    @Transactional(readOnly = true)
    override fun findActiveExecutionsBySessionId(sessionId: UUID): List<ExecutionStatusView> = query(
        whereClause = "where execution.session_id = :sessionId and execution.status in (:activeStatuses)",
        parameters = mapOf(
            "sessionId" to sessionId,
            "activeStatuses" to ACTIVE_STATUSES.map(Enum<*>::name),
        ),
    )

    @Transactional(readOnly = true)
    override fun findActiveExecutions(): List<ExecutionStatusView> = query(
        whereClause = "where execution.status in (:activeStatuses)",
        parameters = mapOf("activeStatuses" to ACTIVE_STATUSES.map(Enum<*>::name)),
    )

    private fun query(whereClause: String, parameters: Map<String, Any>): List<ExecutionStatusView> = jdbcTemplate.query(
        """
        with queued_executions as (
            select
                id,
                row_number() over (order by queued_at asc, id asc) as queue_position
            from executions
            where status = 'QUEUED'
              and queued_at is not null
        )
        select
            execution.id,
            execution.session_id,
            execution.block_version,
            execution.status,
            queued.queue_position,
            execution.assigned_robot_id,
            execution.queued_at,
            execution.started_at,
            execution.finished_at
        from executions execution
        left join queued_executions queued on queued.id = execution.id
        $whereClause
        order by execution.queued_at asc nulls last, execution.id asc
        """.trimIndent(),
        parameters,
    ) { resultSet, _ -> resultSet.toView() }

    private fun ResultSet.toView(): ExecutionStatusView = ExecutionStatusView(
        executionId = getObject("id", UUID::class.java),
        sessionId = getObject("session_id", UUID::class.java),
        blockVersion = getNullableLong("block_version"),
        status = ExecutionStatus.valueOf(getString("status")),
        queuePosition = getNullableInt("queue_position"),
        assignedRobotId = getObject("assigned_robot_id", UUID::class.java),
        queuedAt = getNullableInstant("queued_at"),
        startedAt = getNullableInstant("started_at"),
        finishedAt = getNullableInstant("finished_at"),
    )

    private fun ResultSet.getNullableInt(column: String): Int? = getInt(column).let { value ->
        if (wasNull()) null else value
    }

    private fun ResultSet.getNullableLong(column: String): Long? = getLong(column).let { value ->
        if (wasNull()) null else value
    }

    private fun ResultSet.getNullableInstant(column: String): Instant? = getTimestamp(column)?.toInstant()

    companion object {
        private val ACTIVE_STATUSES = setOf(
            ExecutionStatus.QUEUED,
            ExecutionStatus.ASSIGNED,
            ExecutionStatus.RUNNING,
        )
    }
}
