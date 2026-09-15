package team.inreok.poppyserver.domain.execution.infrastructure

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.application.ExecutionStatusQueryRepository
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.session.application.BlockRevisionRepository
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.model.BlockRevision
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
class ExecutionStatusQueryIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var executionStatusQueryRepository: ExecutionStatusQueryRepository

    @Autowired
    lateinit var sessionRepository: SessionRepository

    @Autowired
    lateinit var blockRevisionRepository: BlockRevisionRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    private lateinit var sessionId: UUID

    @BeforeEach
    fun cleanExecutions() {
        inTransaction {
            jdbcTemplate.update("delete from executions")
            val session = sessionRepository.save(Session.create(Instant.parse("2026-01-01T00:00:00Z")))
            blockRevisionRepository.save(BlockRevision.create(session.id, 1L, "{}"))
            sessionId = session.id
        }
    }

    @Test
    @Transactional
    fun `active Execution read model은 FIFO queue position과 projection을 제공한다`() {
        val queuedAt = Instant.parse("2000-01-01T00:00:00Z")
        val firstId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val secondId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val thirdId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val assignedRobotId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val startedAt = Instant.parse("2000-01-01T00:01:00Z")
        val finishedAt = Instant.parse("2000-01-01T00:02:00Z")

        executionRepository.save(Execution.restore(firstId, ExecutionStatus.QUEUED, sessionId = sessionId, blockVersion = 1L, queuedAt = queuedAt))
        executionRepository.save(Execution.restore(secondId, ExecutionStatus.QUEUED, sessionId = sessionId, blockVersion = 1L, queuedAt = queuedAt))
        executionRepository.save(Execution.restore(thirdId, ExecutionStatus.QUEUED, sessionId = sessionId, blockVersion = 1L, queuedAt = queuedAt))
        val assigned = Execution.restore(
            UUID.fromString("00000000-0000-0000-0000-000000000010"),
            ExecutionStatus.ASSIGNED,
            assignedRobotId = assignedRobotId,
            sessionId = sessionId,
            blockVersion = 1L,
            queuedAt = queuedAt,
        )
        val running = Execution.restore(
            UUID.fromString("00000000-0000-0000-0000-000000000011"),
            ExecutionStatus.RUNNING,
            assignedRobotId = assignedRobotId,
            sessionId = sessionId,
            blockVersion = 1L,
            queuedAt = queuedAt,
            startedAt = startedAt,
        )
        val completed = Execution.restore(
            UUID.fromString("00000000-0000-0000-0000-000000000012"),
            ExecutionStatus.COMPLETED,
            sessionId = sessionId,
            blockVersion = 1L,
            queuedAt = queuedAt,
            startedAt = startedAt,
            finishedAt = finishedAt,
        )
        val failed = Execution.restore(
            UUID.fromString("00000000-0000-0000-0000-000000000013"),
            ExecutionStatus.FAILED,
            sessionId = sessionId,
            blockVersion = 1L,
            queuedAt = queuedAt,
            finishedAt = finishedAt,
        )
        val cancelled = Execution.restore(
            UUID.fromString("00000000-0000-0000-0000-000000000014"),
            ExecutionStatus.CANCELLED,
            sessionId = sessionId,
            blockVersion = 1L,
            queuedAt = queuedAt,
            finishedAt = finishedAt,
        )
        val legacy = executionRepository.save(Execution.create())
        listOf(assigned, running, completed, failed, cancelled).forEach(executionRepository::save)

        val active = executionStatusQueryRepository.findActiveExecutions()
        val queueViews = active.filter { it.status == ExecutionStatus.QUEUED && it.sessionId == sessionId }

        assertEquals(listOf(firstId, secondId, thirdId), queueViews.map { it.executionId })
        assertEquals(listOf(1, 2, 3), queueViews.map { it.queuePosition })
        assertEquals(assignedRobotId, active.single { it.executionId == assigned.id }.assignedRobotId)
        assertNull(active.single { it.executionId == assigned.id }.queuePosition)
        assertNull(active.single { it.executionId == running.id }.queuePosition)
        assertNull(active.single { it.executionId == legacy.id }.queuePosition)

        val completedView = assertNotNull(executionStatusQueryRepository.findById(completed.id))
        val failedView = assertNotNull(executionStatusQueryRepository.findById(failed.id))
        val cancelledView = assertNotNull(executionStatusQueryRepository.findById(cancelled.id))
        assertNull(completedView.queuePosition)
        assertEquals(finishedAt, completedView.finishedAt)
        assertNull(failedView.queuePosition)
        assertEquals(finishedAt, failedView.finishedAt)
        assertNull(cancelledView.queuePosition)
        assertEquals(finishedAt, cancelledView.finishedAt)
    }

    @Test
    @Transactional
    fun `active executions can be filtered by session in the database`() {
        val firstSession = sessionRepository.save(Session.create(Instant.parse("2026-01-01T00:00:00Z")))
        val secondSession = sessionRepository.save(Session.create(Instant.parse("2026-01-01T00:00:00Z")))
        blockRevisionRepository.save(BlockRevision.create(firstSession.id, 1L, "{}"))
        blockRevisionRepository.save(BlockRevision.create(secondSession.id, 1L, "{}"))
        executionRepository.save(Execution.create(firstSession.id, 1))
        executionRepository.save(Execution.create(secondSession.id, 1))

        val result = executionStatusQueryRepository.findActiveExecutionsBySessionId(firstSession.id)

        assertEquals(1, result.size)
        assertEquals(firstSession.id, result.single().sessionId)
    }

    private fun <T> inTransaction(block: () -> T): T = requireNotNull(
        TransactionTemplate(transactionManager).execute { block() },
    )
}
