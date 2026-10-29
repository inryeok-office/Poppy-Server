package team.inreok.poppyserver.domain.robot.application

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotAvailabilityStatus
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus

class RobotAvailabilityServiceTest {

    private val now = Instant.parse("2026-10-29T08:00:00Z")

    @Test
    fun `heartbeat 상태로 Robot별 가용 상태를 계산한다`() {
        val service = service(
            online("GO2EDU001", RobotOperationStatus.READY),
            online("GO2EDU002", RobotOperationStatus.READY, currentExecutionId = UUID.randomUUID()),
            online("GO2EDU003", RobotOperationStatus.UNAVAILABLE),
            Robot.register(alias = "GO2EDU004", model = "GO2"),
        )

        val summary = service.summarize()

        assertEquals(1, summary.availableCount)
        assertEquals(4, summary.total)
        assertEquals(
            listOf(
                RobotAvailabilityItem("GO2EDU001", RobotAvailabilityStatus.READY),
                RobotAvailabilityItem("GO2EDU002", RobotAvailabilityStatus.BUSY),
                RobotAvailabilityItem("GO2EDU003", RobotAvailabilityStatus.UNAVAILABLE),
                RobotAvailabilityItem("GO2EDU004", RobotAvailabilityStatus.OFFLINE),
            ),
            summary.robots,
        )
    }

    @Test
    fun `heartbeat가 끊겨 OFFLINE 처리된 Robot은 배정 정보가 남아 있어도 OFFLINE이다`() {
        val robot = online("GO2EDU001", RobotOperationStatus.READY, currentExecutionId = UUID.randomUUID())
        robot.markOffline()

        val summary = service(robot).summarize()

        assertEquals(0, summary.availableCount)
        assertEquals(RobotAvailabilityStatus.OFFLINE, summary.robots.single().status)
    }

    @Test
    fun `비활성화된 Robot은 목록과 total에서 제외한다`() {
        val retired = online("GO2EDU009", RobotOperationStatus.READY).apply { deactivate() }

        val summary = service(online("GO2EDU001", RobotOperationStatus.READY), retired).summarize()

        assertEquals(1, summary.total)
        assertEquals(listOf("GO2EDU001"), summary.robots.map { it.alias })
    }

    @Test
    fun `Robot이 없으면 빈 목록과 0을 반환한다`() {
        val summary = service().summarize()

        assertEquals(0, summary.availableCount)
        assertEquals(0, summary.total)
        assertEquals(emptyList(), summary.robots)
    }

    private fun service(vararg robots: Robot): RobotAvailabilityService =
        RobotAvailabilityService(ListRobotRepository(robots.toList()))

    private fun online(
        alias: String,
        operationStatus: RobotOperationStatus,
        currentExecutionId: UUID? = null,
    ): Robot = Robot.register(alias = alias, model = "GO2").apply {
        applyHeartbeat(
            at = now,
            connectionStatus = RobotConnectionStatus.ONLINE,
            operationStatus = operationStatus,
            batteryPercent = 70,
            currentExecutionId = currentExecutionId,
        )
    }

    private class ListRobotRepository(
        private val robots: List<Robot>,
    ) : RobotRepository {
        override fun findAll(
            operationStatus: RobotOperationStatus?,
            connectionStatus: RobotConnectionStatus?,
        ): List<Robot> = robots.reversed()

        override fun save(robot: Robot): Robot = throw UnsupportedOperationException()

        override fun findById(id: UUID): Robot? = throw UnsupportedOperationException()

        override fun findByIdForStatusUpdate(id: UUID): Robot? = throw UnsupportedOperationException()

        override fun findAvailableForAllocation(): Robot? = throw UnsupportedOperationException()

        override fun findAvailableForAllocationCandidateIds(): List<UUID> = throw UnsupportedOperationException()

        override fun findAllById(ids: Collection<UUID>): List<Robot> = throw UnsupportedOperationException()

        override fun saveAll(robots: Collection<Robot>): List<Robot> = throw UnsupportedOperationException()

        override fun existsByAgentId(agentId: UUID): Boolean = throw UnsupportedOperationException()
    }
}
