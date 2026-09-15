package team.inreok.poppyserver.domain.execution.infrastructure

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.execution.application.ExecutionCancellationService
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.application.QueueDispatchService
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.domain.session.application.BlockRevisionRepository
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.model.BlockRevision
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(properties = ["poppy.execution.queue-dispatch-enabled=false"])
class QueueDispatchIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var queueDispatchService: QueueDispatchService

    @Autowired
    lateinit var executionCancellationService: ExecutionCancellationService

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var sessionRepository: SessionRepository

    @Autowired
    lateinit var blockRevisionRepository: BlockRevisionRepository

    private lateinit var fixtureSessionId: UUID

    @BeforeEach
    fun cleanExecutionFixtures() {
        inTransaction {
            jdbcTemplate.update("delete from executions")
            jdbcTemplate.update("delete from robot_capabilities")
            jdbcTemplate.update("delete from robots")
            val session = sessionRepository.save(Session.create(Instant.parse("2026-01-01T00:00:00Z")))
            blockRevisionRepository.save(BlockRevision.create(session.id, 1L, "{}"))
            fixtureSessionId = session.id
        }
    }

    @Test
    fun `queuedAt 오름차순으로 FIFO 배정한다`() {
        val executions = inTransaction {
            listOf(
                saveQueued("2026-01-01T00:00:01Z"),
                saveQueued("2026-01-01T00:00:02Z"),
                saveQueued("2026-01-01T00:00:03Z"),
            )
        }
        inTransaction { robotRepository.save(availableRobot()) }

        assertEquals(1, queueDispatchService.dispatchAvailable())
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(executions[0].id)?.status)
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(executions[1].id)?.status)
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(executions[2].id)?.status)
    }

    @Test
    fun `동일 queuedAt은 Execution id 순서로 deterministic하게 배정한다`() {
        val queuedAt = Instant.parse("2026-01-02T00:00:00Z")
        val executions = inTransaction {
            listOf(
                saveQueued(UUID.fromString("00000000-0000-0000-0000-000000000001"), queuedAt),
                saveQueued(UUID.fromString("00000000-0000-0000-0000-000000000002"), queuedAt),
            )
        }
        inTransaction { robotRepository.save(availableRobot()) }

        queueDispatchService.dispatchAvailable()

        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(executions.first().id)?.status)
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(executions.last().id)?.status)
    }

    @Test
    fun `READY Robot 두 대가 있으면 같은 tick에서 앞선 두 건을 배정한다`() {
        val executions = inTransaction {
            listOf(
                saveQueued("2026-01-03T00:00:01Z"),
                saveQueued("2026-01-03T00:00:02Z"),
                saveQueued("2026-01-03T00:00:03Z"),
            )
        }
        inTransaction {
            robotRepository.save(availableRobot())
            robotRepository.save(availableRobot())
        }

        assertEquals(2, queueDispatchService.dispatchAvailable())
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(executions[0].id)?.status)
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(executions[1].id)?.status)
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(executions[2].id)?.status)
    }

    @Test
    fun `사용 가능한 Robot이 없으면 QUEUED를 유지하고 한 번만 조회한다`() {
        val execution = inTransaction { saveQueued("2026-01-04T00:00:00Z") }

        assertEquals(0, queueDispatchService.dispatchAvailable())
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(execution.id)?.status)
    }

    @Test
    fun `OFFLINE UNAVAILABLE inactive occupied Robot은 배정하지 않는다`() {
        val executions = inTransaction {
            listOf(
                saveQueued("2026-01-05T00:00:01Z"),
                saveQueued("2026-01-05T00:00:02Z"),
                saveQueued("2026-01-05T00:00:03Z"),
                saveQueued("2026-01-05T00:00:04Z"),
            )
        }
        inTransaction {
            robotRepository.save(robot(connectionStatus = RobotConnectionStatus.OFFLINE))
            robotRepository.save(robot(operationStatus = RobotOperationStatus.UNAVAILABLE))
            robotRepository.save(inactiveRobot())
            robotRepository.save(robot(currentExecutionId = UUID.randomUUID()))
        }

        assertEquals(0, queueDispatchService.dispatchAvailable())
        executions.forEach { execution ->
            assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(execution.id)?.status)
        }
    }

    @Test
    fun `CANCELLED terminal legacy Execution은 Queue 대상에서 제외한다`() {
        val executions = inTransaction {
            val queued = saveQueued("2026-01-06T00:00:01Z")
            val cancelled = saveQueued("2026-01-06T00:00:02Z").transition { cancel() }
            val completed = saveQueued("2026-01-06T00:00:03Z").transition {
                assign()
                start()
                complete()
            }
            val legacy = executionRepository.save(Execution.create())
            listOf(queued, cancelled, completed, legacy)
        }
        inTransaction { robotRepository.save(availableRobot()) }

        assertEquals(1, queueDispatchService.dispatchAvailable())
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(executions[0].id)?.status)
        assertEquals(ExecutionStatus.CANCELLED, executionRepository.findById(executions[1].id)?.status)
        assertEquals(ExecutionStatus.COMPLETED, executionRepository.findById(executions[2].id)?.status)
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(executions[3].id)?.status)
    }

    @Test
    fun `Robot release 후 다음 Queue를 배정한다`() {
        val fixture = inTransaction {
            val assignedExecution = saveQueued("2026-01-07T00:00:01Z")
            val robot = robotRepository.save(availableRobot())
            assignedExecution.assignToRobot(robot.id)
            executionRepository.save(assignedExecution)
            robot.assignExecution(assignedExecution.id)
            robotRepository.save(robot)
            val queuedExecution = saveQueued("2026-01-07T00:00:02Z")
            assignedExecution to queuedExecution
        }

        executionCancellationService.cancel(fixture.first.id)

        assertEquals(1, queueDispatchService.dispatchAvailable())
        assertEquals(ExecutionStatus.CANCELLED, executionRepository.findById(fixture.first.id)?.status)
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(fixture.second.id)?.status)
    }

    @Test
    fun `반복 실행은 이미 배정된 Execution을 중복 배정하지 않는다`() {
        val execution = inTransaction { saveQueued("2026-01-08T00:00:00Z") }
        inTransaction { robotRepository.save(availableRobot()) }

        assertEquals(1, queueDispatchService.dispatchAvailable())
        assertEquals(0, queueDispatchService.dispatchAvailable())
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(execution.id)?.status)
    }

    @Test
    fun `동시 dispatch에서도 동일 Execution과 Robot을 중복 점유하지 않는다`() {
        val executions = inTransaction {
            listOf(
                saveQueued("2026-01-09T00:00:01Z"),
                saveQueued("2026-01-09T00:00:02Z"),
            )
        }
        val robots = inTransaction {
            listOf(robotRepository.save(availableRobot()), robotRepository.save(availableRobot()))
        }
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val futures = (1..2).map {
            executor.submit {
                ready.countDown()
                check(ready.await(10, TimeUnit.SECONDS))
                check(start.await(10, TimeUnit.SECONDS))
                queueDispatchService.dispatchAvailable()
            }
        }
        check(ready.await(10, TimeUnit.SECONDS))
        start.countDown()

        try {
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(2, executions.count { executionRepository.findById(it.id)?.status == ExecutionStatus.ASSIGNED })
        assertEquals(2, robots.count { robotRepository.findById(it.id)?.currentExecutionId != null })
        assertEquals(2, executions.mapNotNull { executionRepository.findById(it.id)?.assignedRobotId }.toSet().size)
    }

    private fun saveQueued(queuedAt: String): Execution = saveQueued(Instant.parse(queuedAt))

    private fun saveQueued(queuedAt: Instant): Execution = executionRepository.save(
        Execution.create(fixtureSessionId, 1L, queuedAt),
    )

    private fun saveQueued(id: UUID, queuedAt: Instant): Execution = executionRepository.save(
        Execution.restore(
            id = id,
            status = ExecutionStatus.QUEUED,
            sessionId = fixtureSessionId,
            blockVersion = 1L,
            queuedAt = queuedAt,
        ),
    )

    private fun Execution.transition(action: Execution.() -> Unit): Execution = apply(action).also {
        executionRepository.save(it)
    }

    private fun availableRobot(): Robot = robot()

    private fun robot(
        connectionStatus: RobotConnectionStatus = RobotConnectionStatus.ONLINE,
        operationStatus: RobotOperationStatus = RobotOperationStatus.READY,
        currentExecutionId: UUID? = null,
    ): Robot = Robot.register(
        alias = "queue-${UUID.randomUUID()}",
        model = "GO2",
    ).apply {
        if (connectionStatus == RobotConnectionStatus.ONLINE) {
            applyHeartbeat(
                at = Instant.parse("2026-01-01T00:00:00Z"),
                connectionStatus = connectionStatus,
                operationStatus = operationStatus,
                batteryPercent = null,
                currentExecutionId = currentExecutionId,
            )
        } else if (operationStatus == RobotOperationStatus.READY) {
            markReady()
        }
    }

    private fun inactiveRobot(): Robot = robot().apply { deactivate() }

    private fun <T> inTransaction(block: () -> T): T = requireNotNull(
        TransactionTemplate(transactionManager).execute { block() },
    )
}
