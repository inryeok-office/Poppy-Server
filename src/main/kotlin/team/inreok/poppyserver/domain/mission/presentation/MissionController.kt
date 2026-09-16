package team.inreok.poppyserver.domain.mission.presentation

import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.mission.application.MissionCatalog
import team.inreok.poppyserver.domain.mission.model.CompletionCondition
import team.inreok.poppyserver.domain.mission.model.Mission
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@RequestMapping("/api/v1/missions")
class MissionController(
    private val missionCatalog: MissionCatalog,
) {
    @GetMapping
    fun list(): ApiResponse<MissionListResponse> = ApiResponse.success(
        MissionListResponse(
            missions = missionCatalog.list().map(Mission::toSummaryResponse),
        ),
    )

    @GetMapping("/{missionId}")
    fun get(@PathVariable missionId: UUID): ApiResponse<MissionDetailResponse> =
        ApiResponse.success(missionCatalog.getById(missionId).toDetailResponse())
}

data class MissionListResponse(
    val missions: List<MissionSummaryResponse>,
)

data class MissionSummaryResponse(
    val missionId: UUID,
    val title: String,
    val difficulty: String,
    val summary: String,
    val estimatedSeconds: Long?,
)

data class MissionDetailResponse(
    val missionId: UUID,
    val title: String,
    val difficulty: String,
    val description: String,
    val goal: String,
    val completionCondition: CompletionConditionResponse,
    val timeLimitSeconds: Long?,
    val allowedBlocks: List<String>,
)

data class CompletionConditionResponse(
    val description: String,
)

private fun Mission.toSummaryResponse(): MissionSummaryResponse = MissionSummaryResponse(
    missionId = id,
    title = title,
    difficulty = difficulty.name,
    summary = summary,
    estimatedSeconds = estimatedSeconds,
)

private fun Mission.toDetailResponse(): MissionDetailResponse = MissionDetailResponse(
    missionId = id,
    title = title,
    difficulty = difficulty.name,
    description = description,
    goal = goal,
    completionCondition = completionCondition.toResponse(),
    timeLimitSeconds = timeLimitSeconds,
    allowedBlocks = allowedBlocks.map { it.name },
)

private fun CompletionCondition.toResponse(): CompletionConditionResponse = CompletionConditionResponse(
    description = description,
)
