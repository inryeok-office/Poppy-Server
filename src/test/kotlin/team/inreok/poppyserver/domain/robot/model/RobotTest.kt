package team.inreok.poppyserver.domain.robot.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RobotTest {

    @Test
    fun `등록 직후 기본 상태를 가진다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")

        assertEquals(RobotConnectionStatus.OFFLINE, robot.connectionStatus)
        assertEquals(RobotOperationStatus.UNAVAILABLE, robot.operationStatus)
        assertTrue(robot.active)
        assertTrue(robot.capabilities.isEmpty())
        assertNull(robot.lastHeartbeatAt)
        assertNull(robot.firmwareVersion)
        assertNull(robot.sdkVersion)
    }

    @Test
    fun `alias가 비어 있으면 등록에 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            Robot.register(alias = " ", model = "GO2 EDU")
        }
    }

    @Test
    fun `model이 비어 있으면 등록에 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            Robot.register(alias = "행사장-1호기", model = " ")
        }
    }

    @Test
    fun `heartbeat를 기록하면 시간이 갱신되고 온라인 상태가 된다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")
        val now = Instant.parse("2026-08-24T00:00:00Z")

        robot.recordHeartbeat(now)

        assertEquals(now, robot.lastHeartbeatAt)
        assertEquals(RobotConnectionStatus.ONLINE, robot.connectionStatus)
    }

    @Test
    fun `비활성화된 Robot은 heartbeat를 기록할 수 없다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")
        robot.deactivate()

        assertFailsWith<IllegalStateException> {
            robot.recordHeartbeat(Instant.now())
        }
    }

    @Test
    fun `markOffline은 연결 상태를 오프라인으로 전환한다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")
        robot.recordHeartbeat(Instant.now())

        robot.markOffline()

        assertEquals(RobotConnectionStatus.OFFLINE, robot.connectionStatus)
    }

    @Test
    fun `markReady와 markUnavailable로 운영 상태를 전환한다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")

        robot.markReady()
        assertEquals(RobotOperationStatus.READY, robot.operationStatus)

        robot.markUnavailable()
        assertEquals(RobotOperationStatus.UNAVAILABLE, robot.operationStatus)
    }

    @Test
    fun `비활성화된 Robot은 준비 상태로 전환할 수 없다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")
        robot.deactivate()

        assertFailsWith<IllegalStateException> {
            robot.markReady()
        }
    }

    @Test
    fun `deactivate는 비활성화하고 운영 상태를 사용 불가로 전환한다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")
        robot.markReady()

        robot.deactivate()

        assertEquals(false, robot.active)
        assertEquals(RobotOperationStatus.UNAVAILABLE, robot.operationStatus)
    }

    @Test
    fun `activate는 다시 활성화하지만 운영 상태는 별도로 전환해야 한다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")
        robot.deactivate()

        robot.activate()

        assertTrue(robot.active)
        assertEquals(RobotOperationStatus.UNAVAILABLE, robot.operationStatus)
    }

    @Test
    fun `capability를 등록하면 기본 상태는 미검증이다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")

        robot.reportCapability("move")

        val capability = robot.capabilities.getValue("MOVE")
        assertEquals("MOVE", capability.code)
        assertEquals(CapabilitySupportStatus.UNVERIFIED, capability.status)
    }

    @Test
    fun `동일한 capability 코드는 대소문자와 공백에 관계없이 하나로 갱신된다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")

        robot.reportCapability(" move ")
        robot.reportCapability("MOVE", CapabilitySupportStatus.VERIFIED)

        assertEquals(1, robot.capabilities.size)
        assertEquals(CapabilitySupportStatus.VERIFIED, robot.capabilities.getValue("MOVE").status)
    }

    @Test
    fun `capability 코드가 비어 있으면 등록에 실패한다`() {
        val robot = Robot.register(alias = "행사장-1호기", model = "GO2 EDU")

        assertFailsWith<IllegalArgumentException> {
            robot.reportCapability("   ")
        }
    }
}
