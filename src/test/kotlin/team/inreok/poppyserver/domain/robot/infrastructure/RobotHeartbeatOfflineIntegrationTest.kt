package team.inreok.poppyserver.domain.robot.infrastructure

import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.application.ExecutionAllocationService
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotHeartbeatTimeoutService
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest

@SpringBootTest
@Transactional
class RobotHeartbeatOfflineIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var heartbeatTimeoutService: RobotHeartbeatTimeoutService

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var allocationService: ExecutionAllocationService

    @Test
    fun `stale Robot을 OFFLINE 처리하고 allocation 대상에서 제외한다`() {
        val now = Instant.parse("2026-09-15T00:00:00Z")
        val robot = robot(now.minusSeconds(91))
        robotRepository.save(robot)

        heartbeatTimeoutService.detectStaleRobots(now)

        assertEquals(RobotConnectionStatus.OFFLINE, robotRepository.findById(robot.id)?.connectionStatus)
        val execution = executionRepository.save(Execution.create())

        assertNull(allocationService.allocate(execution.id))
        assertEquals(ExecutionStatus.QUEUED, executionRepository.findById(execution.id)?.status)
    }

    @Test
    fun `OFFLINE 처리 시 currentExecutionId와 operationStatus를 변경하지 않는다`() {
        val now = Instant.parse("2026-09-15T00:00:00Z")
        val executionId = UUID.randomUUID()
        val robot = robot(now.minusSeconds(91), executionId)
        robotRepository.save(robot)

        heartbeatTimeoutService.detectStaleRobots(now)

        val storedRobot = robotRepository.findById(robot.id)
        assertEquals(RobotConnectionStatus.OFFLINE, storedRobot?.connectionStatus)
        assertEquals(RobotOperationStatus.READY, storedRobot?.operationStatus)
        assertEquals(executionId, storedRobot?.currentExecutionId)
    }

    @Test
    fun `90초 미만 heartbeat와 null heartbeat Robot은 변경하지 않는다`() {
        val now = Instant.parse("2026-09-15T00:00:00Z")
        val freshRobot = robot(now.minusSeconds(89))
        val unconnectedRobot = Robot.register(alias = "unconnected-${UUID.randomUUID()}", model = "GO2")
        robotRepository.save(freshRobot)
        robotRepository.save(unconnectedRobot)

        heartbeatTimeoutService.detectStaleRobots(now)

        assertEquals(RobotConnectionStatus.ONLINE, robotRepository.findById(freshRobot.id)?.connectionStatus)
        assertNull(robotRepository.findById(unconnectedRobot.id)?.lastHeartbeatAt)
        assertEquals(RobotConnectionStatus.OFFLINE, robotRepository.findById(unconnectedRobot.id)?.connectionStatus)
    }

    private fun robot(lastHeartbeatAt: Instant, currentExecutionId: UUID? = null): Robot =
        Robot.register(alias = "robot-${UUID.randomUUID()}", model = "GO2").apply {
            applyHeartbeat(
                at = lastHeartbeatAt,
                connectionStatus = RobotConnectionStatus.ONLINE,
                operationStatus = RobotOperationStatus.READY,
                batteryPercent = 80,
                currentExecutionId = currentExecutionId,
            )
        }
}
