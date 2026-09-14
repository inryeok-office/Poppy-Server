package team.inreok.poppyserver.domain.execution.presentation

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.execution.application.ExecutionCancellationResult
import team.inreok.poppyserver.domain.execution.application.ExecutionCancellationService
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/executions")
class ExecutionCancellationController(
    private val executionCancellationService: ExecutionCancellationService,
) {
    @PostMapping("/{executionId}/cancel")
    fun cancel(@PathVariable executionId: UUID): ApiResponse<ExecutionCancellationResponse> = ApiResponse.success(
        executionCancellationService.cancel(executionId).toResponse(),
    )
}

data class ExecutionCancellationResponse(
    val executionId: UUID,
    val status: String,
)

private fun ExecutionCancellationResult.toResponse(): ExecutionCancellationResponse = ExecutionCancellationResponse(
    executionId = executionId,
    status = status.name,
)
