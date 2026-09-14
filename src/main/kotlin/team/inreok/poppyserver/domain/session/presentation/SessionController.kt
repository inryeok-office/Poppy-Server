package team.inreok.poppyserver.domain.session.presentation

import java.util.UUID
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import team.inreok.poppyserver.domain.session.application.BlockRevisionAppendResult
import team.inreok.poppyserver.domain.session.application.SessionCreationResult
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/sessions")
class SessionController(
    private val sessionService: SessionService,
) {
    @PostMapping
    fun createSession(): ResponseEntity<ApiResponse<SessionResponse>> = ResponseEntity.status(HttpStatus.CREATED).body(
        ApiResponse.success(sessionService.createSession().toResponse()),
    )

    @PostMapping("/{sessionId}/block-revisions")
    fun appendBlockRevision(
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: BlockRevisionRequest,
    ): ResponseEntity<ApiResponse<BlockRevisionResponse>> = ResponseEntity.status(HttpStatus.CREATED).body(
        ApiResponse.success(sessionService.appendBlockRevision(sessionId, request.toDocument()).toResponse()),
    )
}

data class BlockRevisionRequest(
    @field:NotNull val document: JsonNode?,
) {
    fun toDocument(): String = requireNotNull(document).toString()
}

data class SessionResponse(
    val sessionId: UUID,
    val currentBlockVersion: Long,
)

data class BlockRevisionResponse(
    val sessionId: UUID,
    val blockVersion: Long,
)

private fun SessionCreationResult.toResponse(): SessionResponse = SessionResponse(
    sessionId = sessionId,
    currentBlockVersion = currentBlockVersion,
)

private fun BlockRevisionAppendResult.toResponse(): BlockRevisionResponse = BlockRevisionResponse(
    sessionId = sessionId,
    blockVersion = blockVersion,
)
