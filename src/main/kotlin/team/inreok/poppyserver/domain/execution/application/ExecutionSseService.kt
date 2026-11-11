package team.inreok.poppyserver.domain.execution.application

import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
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

        try {
            executionStatusQueryRepository.findActiveExecutionsBySessionId(sessionId)
                .maxWithOrNull(compareBy<ExecutionStatusView> { it.queuedAt ?: Instant.MIN }.thenBy { it.executionId })
                ?.let { view ->
                    if (!send(sessionId, emitter, view)) {
                        remove(sessionId, emitter)
                    }
                }
        } catch (exception: RuntimeException) {
            remove(sessionId, emitter)
            emitter.completeWithError(exception)
            throw exception
        }
        return emitter
    }

    /**
     * 이벤트가 없는 동안에도 SSE 주석 줄(`:keepalive`)을 보내 연결이 유휴로 끊기지 않게 한다.
     * 중간 프록시(예: Next.js 재작성 프록시는 기본 30초 무응답이면 연결을 끊는다)가 있으면
     * 하트비트가 없을 때 30초마다 끊기고 다시 붙는다. 클라이언트는 주석 줄을 무시한다.
     */
    @Scheduled(fixedDelayString = "\${poppy.execution.sse-heartbeat-milliseconds:15000}")
    fun sendHeartbeat() {
        emitters.forEach { (sessionId, sessionEmitters) ->
            sessionEmitters.toList().forEach { emitter ->
                try {
                    emitter.send(SseEmitter.event().comment(HEARTBEAT_COMMENT))
                } catch (_: IOException) {
                    remove(sessionId, emitter)
                } catch (_: IllegalStateException) {
                    remove(sessionId, emitter)
                }
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publish(event: ExecutionStatusChangedEvent) {
        try {
            val views = if (event.status == ExecutionStatus.QUEUED ||
                event.status == ExecutionStatus.ASSIGNED ||
                event.status == ExecutionStatus.CANCELLED
            ) {
                (executionStatusQueryRepository.findActiveExecutions() +
                    listOfNotNull(executionStatusQueryRepository.findById(event.executionId)))
                    .distinctBy { it.executionId }
            } else {
                listOfNotNull(executionStatusQueryRepository.findById(event.executionId))
            }
            views.forEach { view ->
                val sessionId = view.sessionId ?: return@forEach
                val sessionEmitters = emitters[sessionId] ?: return@forEach
                sessionEmitters.toList().forEach { emitter ->
                    if (!send(sessionId, emitter, view)) {
                        remove(sessionId, emitter)
                    }
                }
            }
        } catch (exception: RuntimeException) {
            logger.warn("Failed to publish execution status SSE event", exception)
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
        const val HEARTBEAT_COMMENT = "keepalive"
        private val logger = LoggerFactory.getLogger(ExecutionSseService::class.java)
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
