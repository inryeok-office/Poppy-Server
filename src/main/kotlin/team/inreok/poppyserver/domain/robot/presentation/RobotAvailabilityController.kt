package team.inreok.poppyserver.domain.robot.presentation

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.robot.application.RobotAvailabilityService
import team.inreok.poppyserver.domain.robot.application.RobotAvailabilitySummary
import team.inreok.poppyserver.domain.robot.model.RobotAvailabilityStatus
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@ConditionalOnBean(RobotAvailabilityService::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/robots")
class RobotAvailabilityController(
    private val robotAvailabilityService: RobotAvailabilityService,
) {
    @GetMapping("/availability")
    fun availability(): ApiResponse<RobotAvailabilityResponse> =
        ApiResponse.success(robotAvailabilityService.summarize().toResponse())
}

data class RobotAvailabilityResponse(
    val availableCount: Int,
    val total: Int,
    val robots: List<RobotAvailabilityItemResponse>,
)

data class RobotAvailabilityItemResponse(
    val alias: String,
    val status: RobotAvailabilityStatus,
)

private fun RobotAvailabilitySummary.toResponse(): RobotAvailabilityResponse = RobotAvailabilityResponse(
    availableCount = availableCount,
    total = total,
    robots = robots.map { RobotAvailabilityItemResponse(alias = it.alias, status = it.status) },
)
