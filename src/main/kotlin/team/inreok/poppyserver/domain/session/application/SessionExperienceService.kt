package team.inreok.poppyserver.domain.session.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.mission.application.MissionCatalog
import team.inreok.poppyserver.domain.session.model.ExperienceMode
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.global.response.FieldErrorItem

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionExperienceService(
    private val sessionRepository: SessionRepository,
    private val simulationPassRepository: SimulationPassRepository,
    private val executionRepository: ExecutionRepository,
    private val missionCatalog: MissionCatalog,
) {
    @Transactional
    fun changeExperience(sessionId: UUID, mode: ExperienceMode, missionId: UUID?): SessionExperienceResult {
        validateMissionId(mode, missionId)
        val session = sessionRepository.findByIdForUpdate(sessionId)
            ?: throw ApplicationException(ErrorCode.SESSION_NOT_FOUND)
        if (missionId != null) {
            missionCatalog.getById(missionId)
        }
        if (executionRepository.findActiveBySessionId(sessionId) != null) {
            throw ApplicationException(ErrorCode.EXECUTION_SESSION_ACTIVE)
        }
        if (session.changeExperience(mode, missionId)) {
            sessionRepository.save(session)
            simulationPassRepository.deleteAllBySessionId(sessionId)
        }
        return SessionExperienceResult(
            sessionId = session.id,
            mode = mode,
            missionId = missionId,
            blockVersion = session.currentBlockVersion,
        )
    }

    private fun validateMissionId(mode: ExperienceMode, missionId: UUID?) {
        val reason = when {
            mode == ExperienceMode.MISSION && missionId == null -> "must not be null when mode is MISSION"
            mode == ExperienceMode.FREE && missionId != null -> "must be null when mode is FREE"
            else -> return
        }
        throw ApplicationException(
            errorCode = ErrorCode.INVALID_INPUT,
            fieldErrors = listOf(FieldErrorItem(field = "missionId", reason = reason)),
        )
    }
}

data class SessionExperienceResult(
    val sessionId: UUID,
    val mode: ExperienceMode,
    val missionId: UUID?,
    val blockVersion: Long,
)
