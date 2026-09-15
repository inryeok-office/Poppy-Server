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
        select id, current_execution_id
        from robots
        where connection_status = 'OFFLINE'
          and current_execution_id is not null
        order by id
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
