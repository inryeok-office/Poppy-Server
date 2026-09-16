package team.inreok.poppyserver.domain.robot.application

import org.junit.jupiter.api.Test
import team.inreok.poppyserver.domain.robot.model.CapabilitySupportStatus
import team.inreok.poppyserver.domain.robot.model.RobotCapability
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotCapabilityMatcherTest {
    private val matcher = RobotCapabilityMatcher()

    @Test
    fun `empty requirements match any robot`() {
        assertTrue(matcher.matches(emptyList(), emptySet()))
    }

    @Test
    fun `all required capabilities must be verified`() {
        val capabilities = listOf(
            RobotCapability(" COMMAND_MOVE ", CapabilitySupportStatus.VERIFIED),
            RobotCapability("COMMAND_TURN", CapabilitySupportStatus.VERIFIED),
        )

        assertTrue(matcher.matches(capabilities, setOf("COMMAND_MOVE", "COMMAND_TURN")))
        assertFalse(matcher.matches(capabilities, setOf("COMMAND_MOVE", "COMMAND_TURN", "COMMAND_STOP")))
    }

    @Test
    fun `unverified and unsupported capabilities do not match`() {
        assertFalse(
            matcher.matches(
                listOf(RobotCapability("COMMAND_MOVE", CapabilitySupportStatus.UNVERIFIED)),
                setOf("COMMAND_MOVE"),
            ),
        )
        assertFalse(
            matcher.matches(
                listOf(RobotCapability("COMMAND_MOVE", CapabilitySupportStatus.UNSUPPORTED)),
                setOf("COMMAND_MOVE"),
            ),
        )
    }

    @Test
    fun `extra and unknown robot capabilities do not affect matching`() {
        assertTrue(
            matcher.matches(
                listOf(
                    RobotCapability("COMMAND_MOVE", CapabilitySupportStatus.VERIFIED),
                    RobotCapability("SOME_FUTURE_FEATURE", CapabilitySupportStatus.UNVERIFIED),
                ),
                setOf("COMMAND_MOVE"),
            ),
        )
    }
}
