package team.inreok.poppyserver.domain.execution.presentation

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.inreok.poppyserver.domain.execution.application.ExecutionSseService

@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/sessions")
class ExecutionSseController(
    private val executionSseService: ExecutionSseService,
) {
    @GetMapping("/{sessionId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @PathVariable sessionId: UUID,
        @RequestHeader(name = "X-Session-Token", required = false) sessionToken: String?,
    ): SseEmitter = executionSseService.subscribe(sessionId, sessionToken)
}
