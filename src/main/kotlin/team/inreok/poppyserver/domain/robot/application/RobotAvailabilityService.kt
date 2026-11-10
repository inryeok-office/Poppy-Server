package team.inreok.poppyserver.domain.robot.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.robot.model.RobotAvailabilityStatus

@Service
@ConditionalOnBean(RobotRepository::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class RobotAvailabilityService(
    private val robotRepository: RobotRepository,
) {
    @Transactional(readOnly = true)
    fun summarize(): RobotAvailabilitySummary {
        val robots = robotRepository.findAll(operationStatus = null, connectionStatus = null)
            .filter { it.active }
            .sortedBy { it.alias }
            .map { RobotAvailabilityItem(alias = it.alias, status = it.availabilityStatus) }
        return RobotAvailabilitySummary(
            availableCount = robots.count { it.status == RobotAvailabilityStatus.READY },
            total = robots.size,
            robots = robots,
        )
    }
}

data class RobotAvailabilitySummary(
    val availableCount: Int,
    val total: Int,
    val robots: List<RobotAvailabilityItem>,
)

data class RobotAvailabilityItem(
    val alias: String,
    val status: RobotAvailabilityStatus,
)
