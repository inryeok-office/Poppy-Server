package team.inreok.poppyserver.domain.execution.infrastructure

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.application.OfflineExecutionRecoveryCandidate
import team.inreok.poppyserver.domain.execution.application.OfflineExecutionRecoveryQueryRepository

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class OfflineExecutionRecoveryQueryPersistenceAdapter(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : OfflineExecutionRecoveryQueryRepository {
    @Transactional(readOnly = true)
    override fun findCandidates(limit: Int): List<OfflineExecutionRecoveryCandidate> = jdbcTemplate.query(
        """
        select robots.id, robots.current_execution_id
        from robots
        join executions execution on execution.id = robots.current_execution_id
        where robots.connection_status = 'OFFLINE'
          and execution.status in ('ASSIGNED', 'RUNNING')
          and execution.assigned_robot_id = robots.id
        order by robots.id
        limit :limit
        """.trimIndent(),
        mapOf("limit" to limit),
    ) { resultSet, _ ->
        OfflineExecutionRecoveryCandidate(
            robotId = resultSet.getObject("id", UUID::class.java),
            executionId = resultSet.getObject("current_execution_id", UUID::class.java),
        )
    }
}
