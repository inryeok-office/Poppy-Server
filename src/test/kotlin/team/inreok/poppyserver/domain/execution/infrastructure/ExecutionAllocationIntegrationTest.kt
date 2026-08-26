package team.inreok.poppyserver.domain.execution.infrastructure

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.execution.application.ExecutionAllocationService
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
class ExecutionAllocationIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var allocationService: ExecutionAllocationService

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    @Transactional
    fun `QUEUED Execution을 사용 가능한 Robot에 배정한다`() {
        val execution = executionRepository.save(Execution.create())
        val robot = robotRepository.save(availableRobot())

        val allocatedRobotId = allocationService.allocate(execution.id)

        assertEquals(robot.id, allocatedRobotId)
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(execution.id)?.status)
        assertEquals(execution.id, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    @Transactional
    fun `사용 가능한 Robot이 없으면 Execution을 QUEUED로 유지한다`() {
        val execution = executionRepository.save(Execution.create())
        val robot = robotRepository.save(
            robot(connectionStatus = RobotConnectionStatus.OFFLINE, operationStatus = RobotOperationStatus.READY),
        )

        assertNull(allocationService.allocate(execution.id))
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(execution.id)?.status)
        assertNull(robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    @Transactional
    fun `UNAVAILABLE Robot은 배정 대상에서 제외한다`() {
        val execution = executionRepository.save(Execution.create())
        val robot = robotRepository.save(robot(operationStatus = RobotOperationStatus.UNAVAILABLE))

        assertNull(allocationService.allocate(execution.id))
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(execution.id)?.status)
        assertNull(robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    @Transactional
    fun `점유된 Robot은 배정 대상에서 제외한다`() {
        val execution = executionRepository.save(Execution.create())
        val currentExecutionId = UUID.randomUUID()
        val robot = robotRepository.save(robot(currentExecutionId = currentExecutionId))

        assertNull(allocationService.allocate(execution.id))
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(execution.id)?.status)
        assertEquals(currentExecutionId, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    @Transactional
    fun `비활성 Robot은 배정 대상에서 제외한다`() {
        val execution = executionRepository.save(Execution.create())
        val robot = Robot.register(alias = "inactive-${UUID.randomUUID()}", model = "GO2").apply {
            applyHeartbeat(
                at = Instant.now(),
                connectionStatus = RobotConnectionStatus.ONLINE,
                operationStatus = RobotOperationStatus.READY,
                batteryPercent = null,
                currentExecutionId = null,
            )
            deactivate()
        }
        robotRepository.save(robot)

        assertNull(allocationService.allocate(execution.id))
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(execution.id)?.status)
        assertNull(robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `이미 배정되었거나 terminal 상태인 Execution은 재배정하지 않는다`() {
        val executionIds = inTransaction {
            val assigned = executionRepository.save(Execution.create().apply { assign() })
            val terminal = executionRepository.save(Execution.create().apply {
                assign()
                start()
                complete()
            })
            assigned.id to terminal.id
        }

        assertFailsWith<IllegalStateException> { allocationService.allocate(executionIds.first) }
        assertFailsWith<IllegalStateException> { allocationService.allocate(executionIds.second) }
        inTransaction {
            assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(executionIds.first)?.status)
            assertEquals(ExecutionStatus.COMPLETED, executionRepository.findById(executionIds.second)?.status)
        }
    }

    @Test
    fun `동시에 두 Execution을 하나의 Robot에 배정해도 하나만 성공한다`() {
        val fixture = inTransaction {
            val robot = robotRepository.save(availableRobot())
            val executions = listOf(
                executionRepository.save(Execution.create()),
                executionRepository.save(Execution.create()),
            )
            AllocationFixture(robot.id, executions.map { it.id })
        }

        val results = allocateConcurrently(fixture.executionIds)
        val assignedExecutionIds = fixture.executionIds.filter { id ->
            executionRepository.findById(id)?.status == ExecutionStatus.ASSIGNED
        }

        assertEquals(1, results.count { it != null })
        assertEquals(1, assignedExecutionIds.size)
        assertEquals(assignedExecutionIds.single(), robotRepository.findById(fixture.robotId)?.currentExecutionId)
    }

    @Test
    fun `동시에 두 Execution을 두 Robot에 배정하면 서로 다른 Robot을 사용한다`() {
        val fixture = inTransaction {
            val robots = listOf(
                robotRepository.save(availableRobot()),
                robotRepository.save(availableRobot()),
            )
            val executions = listOf(
                executionRepository.save(Execution.create()),
                executionRepository.save(Execution.create()),
            )
            AllocationFixture(robots.first().id, executions.map { it.id }, robots.map { it.id })
        }

        val results = allocateConcurrently(fixture.executionIds)
        val allocatedRobotIds = results.filterNotNull()

        assertEquals(2, allocatedRobotIds.size)
        assertEquals(2, allocatedRobotIds.toSet().size)
        fixture.executionIds.forEach { executionId ->
            assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(executionId)?.status)
        }
        fixture.robotIds.forEach { robotId ->
            assertNotNull(robotRepository.findById(robotId)?.currentExecutionId)
        }
    }

    @Test
    fun `동일 Execution에 대한 동시 배정 요청은 하나만 성공한다`() {
        val fixture = inTransaction {
            val robots = listOf(
                robotRepository.save(availableRobot()),
                robotRepository.save(availableRobot()),
            )
            val execution = executionRepository.save(Execution.create())
            AllocationFixture(robots.first().id, listOf(execution.id, execution.id), robots.map { it.id })
        }

        val results = allocateConcurrently(fixture.executionIds, allowRejectedExecution = true)
        val occupiedRobotIds = fixture.robotIds.filter { robotId ->
            robotRepository.findById(robotId)?.currentExecutionId != null
        }

        assertEquals(1, results.count { it != null })
        assertEquals(1, occupiedRobotIds.size)
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(fixture.executionIds.first())?.status)
        inTransaction {
            fixture.robotIds
                .mapNotNull(robotRepository::findById)
                .filterNot { it.occupied }
                .forEach { robot ->
                    robot.deactivate()
                    robotRepository.save(robot)
                }
        }
    }

    private fun allocateConcurrently(
        executionIds: List<UUID>,
        allowRejectedExecution: Boolean = false,
    ): List<UUID?> {
        val executor = Executors.newFixedThreadPool(executionIds.size)
        val ready = CountDownLatch(executionIds.size)
        val start = CountDownLatch(1)
        val futures = executionIds.map { executionId ->
            executor.submit<UUID?> {
                ready.countDown()
                check(ready.await(10, TimeUnit.SECONDS))
                check(start.await(10, TimeUnit.SECONDS))
                try {
                    allocationService.allocate(executionId)
                } catch (_: IllegalStateException) {
                    if (allowRejectedExecution) null else throw IllegalStateException("동시 배정이 실패했습니다")
                }
            }
        }
        check(ready.await(10, TimeUnit.SECONDS))
        start.countDown()
        return try {
            futures.map { future -> future.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun availableRobot(): Robot = robot()

    private fun robot(
        connectionStatus: RobotConnectionStatus = RobotConnectionStatus.ONLINE,
        operationStatus: RobotOperationStatus = RobotOperationStatus.READY,
        currentExecutionId: UUID? = null,
    ): Robot = Robot.register(
        alias = "allocation-${UUID.randomUUID()}",
        model = "GO2",
    ).apply {
        if (connectionStatus == RobotConnectionStatus.ONLINE) {
            applyHeartbeat(
                at = Instant.now(),
                connectionStatus = connectionStatus,
                operationStatus = operationStatus,
                batteryPercent = null,
                currentExecutionId = currentExecutionId,
            )
        } else if (operationStatus == RobotOperationStatus.READY) {
            markReady()
        }
    }

    private fun <T> inTransaction(block: () -> T): T =
        requireNotNull(TransactionTemplate(transactionManager).execute { block() })

    private data class AllocationFixture(
        val robotId: UUID,
        val executionIds: List<UUID>,
        val robotIds: List<UUID> = listOf(robotId),
    )
}
