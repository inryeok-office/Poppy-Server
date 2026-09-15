package team.inreok.poppyserver.domain.execution.application

import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.session.application.SessionAccessVerifier

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ExecutionSseService(
    private val sessionAccessVerifier: SessionAccessVerifier,
    private val executionStatusQueryRepository: ExecutionStatusQueryRepository,
    @Value("\${poppy.execution.sse-timeout-milliseconds:1800000}")
    private val timeoutMilliseconds: Long,
) {
    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArraySet<SseEmitter>>()

    init {
        require(timeoutMilliseconds > 0) { "SSE timeout must be positive" }
    }

    fun subscribe(sessionId: UUID, sessionToken: String?): SseEmitter {
        sessionAccessVerifier.verify(sessionId, sessionToken)
        val emitter = SseEmitter(timeoutMilliseconds)
        emitters.computeIfAbsent(sessionId) { CopyOnWriteArraySet() }.add(emitter)
        emitter.onCompletion { remove(sessionId, emitter) }
        emitter.onTimeout { remove(sessionId, emitter) }
        emitter.onError { remove(sessionId, emitter) }

        executionStatusQueryRepository.findActiveExecutions()
            .asSequence()
            .filter { it.sessionId == sessionId }
            .maxWithOrNull(compareBy<ExecutionStatusView> { it.queuedAt ?: Instant.MIN }.thenBy { it.executionId })
            ?.let { view ->
                if (!send(sessionId, emitter, view)) {
                    remove(sessionId, emitter)
                }
            }
        return emitter
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publish(event: ExecutionStatusChangedEvent) {
        val sessionEmitters = emitters[event.sessionId] ?: return
        val view = executionStatusQueryRepository.findById(event.executionId) ?: return
        sessionEmitters.toList().forEach { emitter ->
            if (!send(event.sessionId, emitter, view)) {
                remove(event.sessionId, emitter)
            }
        }
    }

    private fun send(sessionId: UUID, emitter: SseEmitter, view: ExecutionStatusView): Boolean = try {
        emitter.send(
            SseEmitter.event()
                .name(EVENT_NAME)
                .data(view.toEvent(), MediaType.APPLICATION_JSON),
        )
        true
    } catch (_: IOException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    private fun remove(sessionId: UUID, emitter: SseEmitter) {
        emitters[sessionId]?.let { sessionEmitters ->
            sessionEmitters.remove(emitter)
            if (sessionEmitters.isEmpty()) {
                emitters.remove(sessionId, sessionEmitters)
            }
        }
    }

    companion object {
        const val EVENT_NAME = "execution-status"
    }
}

data class ExecutionSseEvent(
    val executionId: UUID,
    val status: ExecutionStatus,
    val queuePosition: Int?,
    val assignedRobotId: UUID?,
    val startedAt: java.time.Instant?,
    val finishedAt: java.time.Instant?,
)

private fun ExecutionStatusView.toEvent(): ExecutionSseEvent = ExecutionSseEvent(
    executionId = executionId,
    status = status,
    queuePosition = queuePosition,
    assignedRobotId = assignedRobotId,
    startedAt = startedAt,
    finishedAt = finishedAt,
)
