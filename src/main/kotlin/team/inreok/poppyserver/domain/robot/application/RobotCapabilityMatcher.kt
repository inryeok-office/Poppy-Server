package team.inreok.poppyserver.domain.robot.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.robot.model.CapabilitySupportStatus
import team.inreok.poppyserver.domain.robot.model.RobotCapability

@Component
class RobotCapabilityMatcher {
    fun matches(
        robotCapabilities: Collection<RobotCapability>,
        requiredCapabilities: Set<String>,
    ): Boolean {
        val verifiedCodes = robotCapabilities
            .asSequence()
            .filter { it.status == CapabilitySupportStatus.VERIFIED }
            .map { normalize(it.code) }
            .toSet()

        return requiredCapabilities.all { normalize(it) in verifiedCodes }
    }

    private fun normalize(code: String): String = code.trim().uppercase()
}
