package team.inreok.poppyserver.domain.execution.application

import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import team.inreok.poppyserver.domain.block.application.BlockProgramParser
import team.inreok.poppyserver.domain.command.application.BlockProgramCompiler
import team.inreok.poppyserver.domain.command.application.HighLevelCommandProtocolSerializer
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RequiredCapabilitiesResolver
import team.inreok.poppyserver.domain.session.application.BlockRevisionRepository
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.application.SimulationPassRepository
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ExecutionRequestService(
    private val sessionRepository: SessionRepository,
    private val blockRevisionRepository: BlockRevisionRepository,
    private val simulationPassRepository: SimulationPassRepository,
    private val executionRepository: ExecutionRepository,
    private val executionStatusEventPublisher: ExecutionStatusEventPublisher,
    private val blockProgramParser: BlockProgramParser,
    private val blockProgramCompiler: BlockProgramCompiler,
    private val requiredCapabilitiesResolver: RequiredCapabilitiesResolver,
    objectMapper: ObjectMapper,
) {
    private val commandProtocolSerializer = HighLevelCommandProtocolSerializer(objectMapper)

    @Transactional
    fun requestExecution(sessionId: UUID, blockVersion: Long): ExecutionRequestResult {
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
        if (simulationPassRepository.findById(sessionId, blockVersion) == null) {
            throw ApplicationException(ErrorCode.SIMULATION_PASS_NOT_FOUND)
        }
        if (executionRepository.findActiveBySessionId(sessionId) != null) {
            throw ApplicationException(ErrorCode.EXECUTION_SESSION_ACTIVE)
        }

        val compiledProgram = blockProgramCompiler.compile(blockProgramParser.parse(revision.document))
        val execution = executionRepository.save(
            Execution.create(
                sessionId = sessionId,
                blockVersion = blockVersion,
                compiledCommandPayload = commandProtocolSerializer.serialize(compiledProgram),
                requiredCapabilities = requiredCapabilitiesResolver.resolve(compiledProgram),
            ),
        )
        executionStatusEventPublisher.publish(
            ExecutionStatusChangedEvent(
                executionId = execution.id,
                sessionId = sessionId,
                status = execution.status,
            ),
        )
        return ExecutionRequestResult(
            executionId = execution.id,
            sessionId = requireNotNull(execution.sessionId),
            blockVersion = requireNotNull(execution.blockVersion),
            status = execution.status,
            queuedAt = requireNotNull(execution.queuedAt),
        )
    }
}

data class ExecutionRequestResult(
    val executionId: UUID,
    val sessionId: UUID,
    val blockVersion: Long,
    val status: ExecutionStatus,
    val queuedAt: Instant,
)
