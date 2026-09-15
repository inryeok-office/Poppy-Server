package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.session.application.SessionAccessVerifier
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ExecutionStatusQueryService(
    private val executionStatusQueryRepository: ExecutionStatusQueryRepository,
    private val sessionAccessVerifier: SessionAccessVerifier,
) {
    @Transactional(readOnly = true)
    fun find(executionId: UUID, sessionToken: String?): ExecutionStatusResponse {
        val authenticatedSession = sessionAccessVerifier.authenticate(sessionToken)
        val view = executionStatusQueryRepository.findById(executionId)
            ?: throw ApplicationException(ErrorCode.EXECUTION_NOT_FOUND)
        val sessionId = view.sessionId
            ?: throw ApplicationException(ErrorCode.EXECUTION_ACCESS_DENIED)
        if (authenticatedSession.id != sessionId) {
            throw ApplicationException(ErrorCode.EXECUTION_ACCESS_DENIED)
        }
        return view.toResponse()
    }
}

data class ExecutionStatusResponse(
    val executionId: UUID,
    val sessionId: UUID?,
    val blockVersion: Long?,
    val status: String,
    val queuePosition: Int?,
    val assignedRobotId: UUID?,
    val queuedAt: java.time.Instant?,
    val startedAt: java.time.Instant?,
    val finishedAt: java.time.Instant?,
)

private fun ExecutionStatusView.toResponse(): ExecutionStatusResponse = ExecutionStatusResponse(
    executionId = executionId,
    sessionId = sessionId,
    blockVersion = blockVersion,
    status = status.name,
    queuePosition = queuePosition,
    assignedRobotId = assignedRobotId,
    queuedAt = queuedAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
)
