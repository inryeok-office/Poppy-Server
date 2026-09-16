package team.inreok.poppyserver.domain.execution.application

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import tools.jackson.databind.json.JsonMapper
import team.inreok.poppyserver.domain.block.application.BlockProgramParseException
import team.inreok.poppyserver.domain.block.application.BlockProgramParser
import team.inreok.poppyserver.domain.block.application.BlockProgramValidator
import team.inreok.poppyserver.domain.command.application.BlockProgramCompiler
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.robot.application.RequiredCapabilitiesResolver
import team.inreok.poppyserver.domain.session.model.BlockRevision
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.domain.session.model.SimulationPass
import team.inreok.poppyserver.domain.session.application.BlockRevisionRepository
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.application.SimulationPassRepository

class ExecutionRequestServiceTest {

    @Test
    fun `compile failure does not save an execution`() {
        val sessionId = UUID.randomUUID()
        val session = Session.restore(sessionId, 1, Instant.now())
        val revision = BlockRevision.create(sessionId, 1, "{\"legacy\":true}")
        var saved = false
        val executionRepository = object : ExecutionRepository {
            override fun save(execution: Execution): Execution {
                saved = true
                return execution
            }

            override fun findById(id: UUID): Execution? = null
            override fun findByIdForAllocation(id: UUID): Execution? = null
            override fun findByIdForStatusUpdate(id: UUID): Execution? = null
            override fun findActiveBySessionId(sessionId: UUID): Execution? = null
        }
        val service = ExecutionRequestService(
            sessionRepository = object : SessionRepository {
                override fun save(session: Session): Session = session
                override fun findById(id: UUID): Session? = null
                override fun findByIdForUpdate(id: UUID): Session? = session
                override fun findByTokenDigest(tokenDigest: String): Session? = null
                override fun findByRecoveryCodeDigest(recoveryCodeDigest: String): Session? = null
                override fun findByRecoveryCodeDigestForUpdate(recoveryCodeDigest: String): Session? = null
                override fun findInactiveIdsBefore(cutoff: Instant): List<UUID> = emptyList()
            },
            blockRevisionRepository = object : BlockRevisionRepository {
                override fun save(revision: BlockRevision): BlockRevision = revision
                override fun findById(sessionId: UUID, version: Long): BlockRevision? = revision
            },
            simulationPassRepository = object : SimulationPassRepository {
                override fun save(pass: SimulationPass): SimulationPass = pass
                override fun findById(sessionId: UUID, blockVersion: Long): SimulationPass =
                    SimulationPass.create(sessionId, blockVersion)
            },
            executionRepository = executionRepository,
            executionStatusEventPublisher = object : ExecutionStatusEventPublisher {
                override fun publish(event: ExecutionStatusChangedEvent) = Unit
            },
            blockProgramParser = BlockProgramParser(JsonMapper.builder().build()),
            blockProgramCompiler = BlockProgramCompiler(BlockProgramValidator()),
            requiredCapabilitiesResolver = RequiredCapabilitiesResolver(),
            objectMapper = JsonMapper.builder().build(),
        )

        assertFailsWith<BlockProgramParseException> {
            service.requestExecution(sessionId, 1)
        }
        assertFalse(saved)
    }
}
