package team.inreok.poppyserver.domain.robot.application

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus

class RobotHeartbeatTimeoutServiceTest {

    private val now = Instant.parse("2026-09-15T00:00:00Z")

    @Test
    fun `90초 미만 heartbeat Robot은 ONLINE을 유지한다`() {
        val robot = robot(lastHeartbeatAt = now.minusSeconds(89))
        val fixture = fixture(robot)

        fixture.service.detectStaleRobots(now)

        assertEquals(RobotConnectionStatus.ONLINE, robot.connectionStatus)
        assertEquals(0, fixture.repository.saveCount)
    }

    @Test
    fun `cutoff와 같은 시각의 heartbeat Robot은 ONLINE을 유지한다`() {
        val robot = robot(lastHeartbeatAt = now.minusSeconds(90))
        val fixture = fixture(robot)

        fixture.service.detectStaleRobots(now)

        assertEquals(RobotConnectionStatus.ONLINE, robot.connectionStatus)
        assertEquals(0, fixture.repository.saveCount)
    }

    @Test
    fun `90초 초과 heartbeat Robot은 OFFLINE이 되고 실행 정보를 보존한다`() {
        val executionId = UUID.randomUUID()
        val robot = robot(
            lastHeartbeatAt = now.minusSeconds(91),
            operationStatus = RobotOperationStatus.READY,
            currentExecutionId = executionId,
        )
        val fixture = fixture(robot)

        fixture.service.detectStaleRobots(now)

        assertEquals(RobotConnectionStatus.OFFLINE, robot.connectionStatus)
        assertEquals(RobotOperationStatus.READY, robot.operationStatus)
        assertEquals(executionId, robot.currentExecutionId)
        assertEquals(1, fixture.repository.saveCount)
    }

    @Test
    fun `이미 OFFLINE인 Robot은 처리하지 않는다`() {
        val robot = Robot.register(alias = "offline-${UUID.randomUUID()}", model = "GO2")
        robot.markReady()
        val fixture = fixture(robot, candidateIds = listOf(robot.id))

        fixture.service.detectStaleRobots(now)

        assertEquals(RobotConnectionStatus.OFFLINE, robot.connectionStatus)
        assertEquals(0, fixture.repository.saveCount)
    }

    @Test
    fun `candidate 조회 후 heartbeat가 갱신되면 OFFLINE 처리하지 않는다`() {
        val robot = robot(lastHeartbeatAt = now.minusSeconds(91))
        val fixture = fixture(robot, candidateIds = listOf(robot.id))
        robot.recordHeartbeat(now.minusSeconds(1))

        fixture.service.detectStaleRobots(now)

        assertEquals(RobotConnectionStatus.ONLINE, robot.connectionStatus)
        assertEquals(now.minusSeconds(1), robot.lastHeartbeatAt)
        assertEquals(0, fixture.repository.saveCount)
    }

    @Test
    fun `lastHeartbeatAt이 null인 Robot은 변경하지 않는다`() {
        val robot = Robot.register(alias = "unconnected-${UUID.randomUUID()}", model = "GO2")
        val fixture = fixture(robot, candidateIds = listOf(robot.id))

        fixture.service.detectStaleRobots(now)

        assertNull(robot.lastHeartbeatAt)
        assertEquals(RobotConnectionStatus.OFFLINE, robot.connectionStatus)
        assertEquals(0, fixture.repository.saveCount)
    }

    @Test
    fun `여러 Robot 중 stale Robot만 OFFLINE 처리하고 반복 실행은 idempotent하다`() {
        val staleRobot = robot(lastHeartbeatAt = now.minusSeconds(91))
        val freshRobot = robot(lastHeartbeatAt = now.minusSeconds(1))
        val fixture = fixture(staleRobot, freshRobot)

        fixture.service.detectStaleRobots(now)
        fixture.service.detectStaleRobots(now)

        assertEquals(RobotConnectionStatus.OFFLINE, staleRobot.connectionStatus)
        assertEquals(RobotConnectionStatus.ONLINE, freshRobot.connectionStatus)
        assertEquals(1, fixture.repository.saveCount)
    }

    @Test
    fun `stale Robot은 설정된 batch size 단위로 처리한다`() {
        val staleRobots = listOf(
            robot(lastHeartbeatAt = now.minusSeconds(91)),
            robot(lastHeartbeatAt = now.minusSeconds(92)),
        )
        val fixture = fixture(*staleRobots.toTypedArray(), heartbeatOfflineBatchSize = 1)

        fixture.service.detectStaleRobots(now)

        assertEquals(2, fixture.repository.saveCount)
        staleRobots.forEach { robot ->
            assertEquals(RobotConnectionStatus.OFFLINE, robot.connectionStatus)
        }
    }

    @Test
    fun `scheduler 실행은 주입된 Clock을 사용한다`() {
        val robot = robot(lastHeartbeatAt = now.minusSeconds(91))
        val fixture = fixture(
            robot = robot,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        fixture.service.detectStaleRobots()

        assertEquals(RobotConnectionStatus.OFFLINE, robot.connectionStatus)
    }

    @Test
    fun `잘못된 timeout 설정은 서비스 생성 시 거부한다`() {
        val repository = FakeRobotRepository(mutableMapOf())
        val queryRepository = FakeStaleRobotQueryRepository(repository, emptyList())

        assertFailsWith<IllegalArgumentException> {
            RobotHeartbeatTimeoutService(
                staleRobotQueryRepository = queryRepository,
                robotOfflineBatchProcessor = RobotOfflineBatchProcessor(repository),
                heartbeatTimeoutSeconds = 0,
                heartbeatOfflineBatchSize = 100,
            )
        }
    }

    private fun fixture(
        vararg robots: Robot,
        robot: Robot? = null,
        candidateIds: List<UUID>? = null,
        heartbeatOfflineBatchSize: Int = 100,
        clock: Clock = Clock.systemUTC(),
    ): Fixture {
        val allRobots = (robots.toList() + listOfNotNull(robot)).associateBy { it.id }.toMutableMap()
        val repository = FakeRobotRepository(allRobots)
        val queryRepository = FakeStaleRobotQueryRepository(
            repository,
            candidateIds,
        )
        return Fixture(
            service = RobotHeartbeatTimeoutService(
                staleRobotQueryRepository = queryRepository,
                robotOfflineBatchProcessor = RobotOfflineBatchProcessor(repository),
                heartbeatTimeoutSeconds = 90,
                heartbeatOfflineBatchSize = heartbeatOfflineBatchSize,
                clock = clock,
            ),
            repository = repository,
        )
    }

    private fun robot(
        lastHeartbeatAt: Instant,
        operationStatus: RobotOperationStatus = RobotOperationStatus.READY,
        currentExecutionId: UUID? = null,
    ): Robot = Robot.register(
        alias = "robot-${UUID.randomUUID()}",
        model = "GO2",
    ).apply {
        applyHeartbeat(
            at = lastHeartbeatAt,
            connectionStatus = RobotConnectionStatus.ONLINE,
            operationStatus = operationStatus,
            batteryPercent = 80,
            currentExecutionId = currentExecutionId,
        )
    }

    private data class Fixture(
        val service: RobotHeartbeatTimeoutService,
        val repository: FakeRobotRepository,
    )

    private class FakeStaleRobotQueryRepository(
        private val repository: FakeRobotRepository,
        initialCandidateIds: List<UUID>?,
    ) : StaleRobotQueryRepository {
        private var initialCandidateIds: List<UUID>? = initialCandidateIds

        override fun findStaleRobotIds(before: Instant, limit: Int): List<UUID> {
            val initialCandidates = initialCandidateIds
            if (initialCandidates != null) {
                initialCandidateIds = null
                return initialCandidates.take(limit)
            }
            return repository.robots.values
                .filter { it.lastHeartbeatAt?.isBefore(before) == true && it.connectionStatus == RobotConnectionStatus.ONLINE }
                .map { it.id }
                .take(limit)
        }
    }

    private class FakeRobotRepository(
        val robots: MutableMap<UUID, Robot>,
    ) : RobotRepository {
        var saveCount: Int = 0

        override fun save(robot: Robot): Robot {
            saveCount++
            robots[robot.id] = robot
            return robot
        }

        override fun findById(id: UUID): Robot? = robots[id]

        override fun findByIdForStatusUpdate(id: UUID): Robot? = robots[id]

        override fun findAvailableForAllocation(): Robot? = robots.values.firstOrNull {
            it.active &&
                it.connectionStatus == RobotConnectionStatus.ONLINE &&
                it.operationStatus == RobotOperationStatus.READY &&
                !it.occupied
        }

        override fun findAllById(ids: Collection<UUID>): List<Robot> = ids.mapNotNull(robots::get)

        override fun saveAll(robots: Collection<Robot>): List<Robot> = robots.map(::save)

        override fun findAll(
            operationStatus: RobotOperationStatus?,
            connectionStatus: RobotConnectionStatus?,
        ): List<Robot> = robots.values.filter {
            (operationStatus == null || it.operationStatus == operationStatus) &&
                (connectionStatus == null || it.connectionStatus == connectionStatus)
        }

        override fun existsByAgentId(agentId: UUID): Boolean = robots.values.any { it.agentId == agentId }
    }
}
