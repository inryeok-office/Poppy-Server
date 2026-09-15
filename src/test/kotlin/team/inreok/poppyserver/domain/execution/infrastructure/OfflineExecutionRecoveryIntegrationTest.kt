package team.inreok.poppyserver.domain.execution.infrastructure

import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.application.OfflineExecutionRecoveryService
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest

@SpringBootTest(
    properties = [
        "poppy.execution.queue-dispatch-enabled=false",
        "poppy.execution.offline-recovery-enabled=false",
    ],
)
class OfflineExecutionRecoveryIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var recoveryService: OfflineExecutionRecoveryService

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `OFFLINE Robot의 ASSIGNED와 RUNNING Execution을 FAILED로 종결하고 점유를 해제한다`() {
        val fixtures = inTransaction {
            listOf(createFixture(ExecutionStatus.ASSIGNED), createFixture(ExecutionStatus.RUNNING))
        }

        assertEquals(2, recoveryService.recoverOfflineExecutions())

        fixtures.forEach { fixture ->
            val execution = executionRepository.findById(fixture.executionId)
            val robot = robotRepository.findById(fixture.robotId)
            assertEquals(ExecutionStatus.FAILED, execution?.status)
            assertNotNull(execution?.finishedAt)
            assertEquals(fixture.robotId, execution?.assignedRobotId)
            assertNull(robot?.currentExecutionId)
        }
    }

    @Test
    fun `반복 recovery는 이미 처리한 Execution을 다시 변경하지 않는다`() {
        val fixture = inTransaction { createFixture(ExecutionStatus.RUNNING) }

        assertEquals(1, recoveryService.recoverOfflineExecutions())
        val finishedAt = executionRepository.findById(fixture.executionId)?.finishedAt

        assertEquals(0, recoveryService.recoverOfflineExecutions())
        assertEquals(finishedAt, executionRepository.findById(fixture.executionId)?.finishedAt)
        assertNull(robotRepository.findById(fixture.robotId)?.currentExecutionId)
    }

    @Test
    fun `ONLINE Robot은 recovery 대상이 아니다`() {
        val fixture = inTransaction { createFixture(ExecutionStatus.RUNNING, RobotConnectionStatus.ONLINE) }

        assertEquals(0, recoveryService.recoverOfflineExecutions())
        assertEquals(ExecutionStatus.RUNNING, executionRepository.findById(fixture.executionId)?.status)
        assertEquals(fixture.executionId, robotRepository.findById(fixture.robotId)?.currentExecutionId)
    }

    @Test
    fun `terminal Execution은 상태와 Robot 점유를 덮어쓰지 않는다`() {
        val fixture = inTransaction {
            val created = createFixture(ExecutionStatus.RUNNING)
            val execution = executionRepository.findByIdForStatusUpdate(created.executionId)!!
            execution.complete()
            executionRepository.save(execution)
            created
        }

        val finishedAt = executionRepository.findById(fixture.executionId)?.finishedAt

        assertEquals(0, recoveryService.recoverOfflineExecutions())
        assertEquals(ExecutionStatus.COMPLETED, executionRepository.findById(fixture.executionId)?.status)
        assertEquals(finishedAt, executionRepository.findById(fixture.executionId)?.finishedAt)
        assertEquals(fixture.executionId, robotRepository.findById(fixture.robotId)?.currentExecutionId)
    }

    @Test
    fun `Robot과 Execution binding이 다르면 recovery하지 않는다`() {
        val fixture = inTransaction {
            val offlineRobot = robotRepository.save(robot())
            val assignedRobot = robotRepository.save(robot())
            val execution = executionRepository.save(
                Execution.create().apply { assignToRobot(assignedRobot.id) },
            )
            offlineRobot.apply {
                applyHeartbeat(
                    at = Instant.parse("2026-09-15T00:00:00Z"),
                    connectionStatus = RobotConnectionStatus.OFFLINE,
                    operationStatus = RobotOperationStatus.READY,
                    batteryPercent = null,
                    currentExecutionId = execution.id,
                )
            }
            robotRepository.save(offlineRobot)
            RecoveryFixture(offlineRobot.id, execution.id)
        }

        assertEquals(0, recoveryService.recoverOfflineExecutions())
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(fixture.executionId)?.status)
        assertEquals(fixture.executionId, robotRepository.findById(fixture.robotId)?.currentExecutionId)
        assertFalse(robotRepository.findById(fixture.robotId)?.connectionStatus == RobotConnectionStatus.ONLINE)
    }

    private fun createFixture(
        status: ExecutionStatus,
        connectionStatus: RobotConnectionStatus = RobotConnectionStatus.OFFLINE,
    ): RecoveryFixture {
        val robot = robotRepository.save(robot())
        val execution = Execution.create().apply {
            assignToRobot(robot.id)
            if (status == ExecutionStatus.RUNNING) {
                start()
            }
        }
        executionRepository.save(execution)
        robot.applyHeartbeat(
            at = Instant.parse("2026-09-15T00:00:00Z"),
            connectionStatus = connectionStatus,
            operationStatus = RobotOperationStatus.READY,
            batteryPercent = null,
            currentExecutionId = execution.id,
        )
        robotRepository.save(robot)
        return RecoveryFixture(robot.id, execution.id)
    }

    private fun robot(): Robot = Robot.register(
        alias = "recovery-${UUID.randomUUID()}",
        model = "GO2",
    )

    private fun <T> inTransaction(block: () -> T): T = requireNotNull(
        TransactionTemplate(transactionManager).execute { block() },
    )

    private data class RecoveryFixture(
        val robotId: UUID,
        val executionId: UUID,
    )
}
