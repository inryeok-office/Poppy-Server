package team.inreok.poppyserver.domain.ai.presentation

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.ai.application.AiAssistantService
import team.inreok.poppyserver.domain.ai.application.AiHintCommand
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@RequestMapping("/api/v1/ai")
class AiController(
    private val aiAssistantService: AiAssistantService,
) {
    @PostMapping("/program-drafts")
    fun programDraft(@Valid @RequestBody request: AiProgramDraftRequest): ApiResponse<AiProgramDraftResponse> {
        val result = aiAssistantService.draftProgram(request.utterance!!)
        return ApiResponse.success(AiProgramDraftResponse(steps = result.steps, explanation = result.explanation))
    }

    @PostMapping("/hints")
    fun hint(@Valid @RequestBody request: AiHintRequest): ApiResponse<AiHintResponse> {
        val message = aiAssistantService.hint(
            AiHintCommand(
                programLines = request.programLines.orEmpty(),
                violationCode = request.violationCode,
                violationMessage = request.violationMessage,
                violationBlockNumber = request.violationBlockNumber,
            ),
        )
        return ApiResponse.success(AiHintResponse(message))
    }
}

data class AiProgramDraftRequest(
    @field:NotNull @field:Size(max = 1000) val utterance: String?,
)

data class AiProgramDraftResponse(
    val steps: List<Map<String, Any>>,
    val explanation: String,
)

data class AiHintRequest(
    @field:Size(max = 40) val programLines: List<String>?,
    @field:Size(max = 40) val violationCode: String?,
    @field:Size(max = 300) val violationMessage: String?,
    @field:Positive val violationBlockNumber: Int?,
)

data class AiHintResponse(
    val message: String,
)
