package team.inreok.poppyserver.domain.execution.presentation

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.execution.application.ExecutionStatusQueryService
import team.inreok.poppyserver.domain.execution.application.ExecutionStatusResponse
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/executions")
class ExecutionStatusController(
    private val executionStatusQueryService: ExecutionStatusQueryService,
) {
    @GetMapping("/{executionId}")
    fun getStatus(
        @PathVariable executionId: UUID,
        @RequestHeader(name = "X-Session-Token", required = false) sessionToken: String?,
    ): ResponseEntity<ApiResponse<ExecutionStatusResponse>> = ResponseEntity.ok(
        ApiResponse.success(executionStatusQueryService.find(executionId, sessionToken)),
    )
}
