package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.block.application.BlockProgramParser
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.mission.application.MissionCatalog
import team.inreok.poppyserver.domain.mission.model.MissionResult
import team.inreok.poppyserver.domain.session.application.BlockRevisionRepository
import team.inreok.poppyserver.domain.session.application.SessionAccessVerifier
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ExecutionStatusQueryService(
    private val executionStatusQueryRepository: ExecutionStatusQueryRepository,
    private val sessionAccessVerifier: SessionAccessVerifier,
    private val blockRevisionRepository: BlockRevisionRepository,
    private val blockProgramParser: BlockProgramParser,
    private val missionCatalog: MissionCatalog,
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
        return view.toResponse(evaluateMission(view))
    }

    private fun evaluateMission(view: ExecutionStatusView): MissionResultResponse? {
        val missionId = view.missionId ?: return null
        val sessionId = view.sessionId ?: return null
        val blockVersion = view.blockVersion ?: return null
        if (view.status != ExecutionStatus.COMPLETED) {
            return null
        }
        val revision = blockRevisionRepository.findById(sessionId, blockVersion) ?: return null
        val program = blockProgramParser.parse(revision.document)
        return missionCatalog.getById(missionId).completionRule.evaluate(program).toResponse(missionId)
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
    val missionResult: MissionResultResponse?,
)

data class MissionResultResponse(
    val missionId: UUID,
    val passed: Boolean,
    val reasons: List<MissionResultReasonResponse>,
)

data class MissionResultReasonResponse(
    val code: String,
    val detail: String,
)

private fun MissionResult.toResponse(missionId: UUID): MissionResultResponse = MissionResultResponse(
    missionId = missionId,
    passed = passed,
    reasons = reasons.map { MissionResultReasonResponse(code = it.code.name, detail = it.detail) },
)

private fun ExecutionStatusView.toResponse(missionResult: MissionResultResponse?): ExecutionStatusResponse = ExecutionStatusResponse(
    executionId = executionId,
    sessionId = sessionId,
    blockVersion = blockVersion,
    status = status.name,
    queuePosition = queuePosition,
    assignedRobotId = assignedRobotId,
    queuedAt = queuedAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
    missionResult = missionResult,
)
