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
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import team.inreok.poppyserver.domain.session.application.BlockRevisionAppendResult
import team.inreok.poppyserver.domain.session.application.SessionCreationResult
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.domain.session.application.SessionAccessVerifier
import team.inreok.poppyserver.domain.session.application.SessionRecoveryService
import team.inreok.poppyserver.domain.session.application.SessionRecoveryResult
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/sessions")
class SessionController(
    private val sessionService: SessionService,
    private val sessionAccessVerifier: SessionAccessVerifier,
    private val sessionRecoveryService: SessionRecoveryService,
) {
    @PostMapping
    fun createSession(): ResponseEntity<ApiResponse<SessionResponse>> = ResponseEntity.status(HttpStatus.CREATED).body(
        ApiResponse.success(sessionService.createSession().toResponse()),
    )

    @PostMapping("/restore")
    fun restoreSession(
        @Valid @RequestBody request: SessionRestoreRequest,
        httpRequest: jakarta.servlet.http.HttpServletRequest,
    ): ResponseEntity<ApiResponse<SessionRecoveryResponse>> = ResponseEntity.ok(
        ApiResponse.success(sessionRecoveryService.restore(request.recoveryCode, httpRequest.remoteAddr).toResponse()),
    )

    @PostMapping("/{sessionId}/block-revisions")
    fun appendBlockRevision(
        @PathVariable sessionId: UUID,
        @RequestHeader(name = "X-Session-Token", required = false) sessionToken: String?,
        @Valid @RequestBody request: BlockRevisionRequest,
    ): ResponseEntity<ApiResponse<BlockRevisionResponse>> {
        sessionAccessVerifier.verify(sessionId, sessionToken)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(sessionService.appendBlockRevision(sessionId, request.toDocument()).toResponse()),
        )
    }
}

data class BlockRevisionRequest(
    @field:NotNull val document: JsonNode?,
) {
    fun toDocument(): String = requireNotNull(document).toString()
}

data class SessionResponse(
    val sessionId: UUID,
    val sessionToken: String,
    val currentBlockVersion: Long,
    val recoveryCode: String,
)

data class SessionRestoreRequest(
    @field:NotNull val recoveryCode: String?,
)

data class SessionRecoveryResponse(
    val sessionId: UUID,
    val sessionToken: String,
    val recoveryCode: String,
    val currentBlockVersion: Long,
)

data class BlockRevisionResponse(
    val sessionId: UUID,
    val blockVersion: Long,
)

private fun SessionCreationResult.toResponse(): SessionResponse = SessionResponse(
    sessionId = sessionId,
    sessionToken = sessionToken,
    currentBlockVersion = currentBlockVersion,
    recoveryCode = recoveryCode,
)

private fun SessionRecoveryResult.toResponse(): SessionRecoveryResponse = SessionRecoveryResponse(
    sessionId = sessionId,
    sessionToken = sessionToken,
    recoveryCode = recoveryCode,
    currentBlockVersion = currentBlockVersion,
)

private fun BlockRevisionAppendResult.toResponse(): BlockRevisionResponse = BlockRevisionResponse(
    sessionId = sessionId,
    blockVersion = blockVersion,
)
