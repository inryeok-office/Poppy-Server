package team.inreok.poppyserver.domain.execution.presentation

import java.time.Instant
import java.util.UUID
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.execution.application.ExecutionRequestResult
import team.inreok.poppyserver.domain.execution.application.ExecutionRequestService
import team.inreok.poppyserver.domain.session.application.SessionAccessVerifier
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/sessions")
class ExecutionRequestController(
    private val executionRequestService: ExecutionRequestService,
    private val sessionAccessVerifier: SessionAccessVerifier,
) {
    @PostMapping("/{sessionId}/executions")
    fun requestExecution(
        @PathVariable sessionId: UUID,
        @RequestHeader(name = "X-Session-Token", required = false) sessionToken: String?,
        @Valid @RequestBody request: ExecutionRequest,
    ): ResponseEntity<ApiResponse<ExecutionResponse>> {
        sessionAccessVerifier.verify(sessionId, sessionToken)
        val result = executionRequestService.requestExecution(sessionId, requireNotNull(request.blockVersion))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result.toResponse()))
    }
}

data class ExecutionRequest(
    @field:NotNull val blockVersion: Long?,
)

data class ExecutionResponse(
    val executionId: UUID,
    val sessionId: UUID,
    val blockVersion: Long,
    val status: ExecutionStatus,
    val queuedAt: Instant,
)

private fun ExecutionRequestResult.toResponse(): ExecutionResponse = ExecutionResponse(
    executionId = executionId,
    sessionId = sessionId,
    blockVersion = blockVersion,
    status = status,
    queuedAt = queuedAt,
)
