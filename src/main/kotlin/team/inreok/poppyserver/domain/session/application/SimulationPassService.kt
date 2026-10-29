package team.inreok.poppyserver.domain.session.application

import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.block.application.BlockProgramParser
import team.inreok.poppyserver.domain.session.model.SimulationPass
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SimulationPassService(
    private val sessionRepository: SessionRepository,
    private val blockRevisionRepository: BlockRevisionRepository,
    private val simulationPassRepository: SimulationPassRepository,
    private val blockProgramParser: BlockProgramParser,
    private val missionBlockGuard: MissionBlockGuard,
) {
    @Transactional
    fun recordSimulationPass(sessionId: UUID, blockVersion: Long): SimulationPassRecordResult {
        val session = sessionRepository.findByIdForUpdate(sessionId)
            ?: throw ApplicationException(ErrorCode.SESSION_NOT_FOUND)
        if (blockVersion <= 0) {
            throw ApplicationException(ErrorCode.SIMULATION_BLOCK_VERSION_INVALID)
        }
        if (blockVersion != session.currentBlockVersion) {
            throw ApplicationException(ErrorCode.SIMULATION_BLOCK_VERSION_STALE)
        }
        val revision = blockRevisionRepository.findById(sessionId, blockVersion)
            ?: throw ApplicationException(ErrorCode.BLOCK_REVISION_NOT_FOUND)
        missionBlockGuard.ensureAllowed(session, blockProgramParser.parse(revision.document))
        val existing = simulationPassRepository.findById(sessionId, blockVersion)
        val (pass, created) = if (existing != null) {
            existing to false
        } else {
            simulationPassRepository.save(SimulationPass.create(sessionId, blockVersion)) to true
        }
        return SimulationPassRecordResult(
            sessionId = pass.sessionId,
            blockVersion = pass.blockVersion,
            passedAt = pass.passedAt,
            created = created,
        )
    }
}

data class SimulationPassRecordResult(
    val sessionId: UUID,
    val blockVersion: Long,
    val passedAt: java.time.Instant,
    val created: Boolean,
)
