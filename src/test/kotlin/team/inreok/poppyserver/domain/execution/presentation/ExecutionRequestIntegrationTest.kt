package team.inreok.poppyserver.domain.execution.presentation

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.application.ExecutionRequestResult
import team.inreok.poppyserver.domain.execution.application.ExecutionRequestService
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.application.SessionCreationResult
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.domain.session.application.SimulationPassService
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import team.inreok.poppyserver.support.validBlockProgram
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ExecutionRequestIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var executionRequestService: ExecutionRequestService

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var sessionRepository: SessionRepository

    @Autowired
    lateinit var simulationPassService: SimulationPassService

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `current passed revision creates a queued execution with provenance`() {
        val session = preparedSession()

        val result = executionRequestService.requestExecution(session.sessionId, 1)
        val restored = executionRepository.findById(result.executionId)

        assertEquals(session.sessionId, result.sessionId)
        assertEquals(1, result.blockVersion)
        assertEquals(ExecutionStatus.QUEUED, result.status)
        assertNotNull(result.queuedAt)
        assertEquals(session.sessionId, restored?.sessionId)
        assertEquals(1, restored?.blockVersion)
        assertEquals(result.queuedAt, restored?.queuedAt)
        assertEquals(
            """{"protocolVersion":1,"commands":[{"sequence":0,"sourceBlockId":"stop-fixture","type":"STOP","parameters":{}}]}""",
            restored?.compiledCommandPayload,
        )
        assertEquals(setOf("COMMAND_STOP"), restored?.requiredCapabilities)
    }

    @Test
    fun `repeat program is stored as a flat compiled command snapshot`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(
            session.sessionId,
            """{"schemaVersion":1,"blocks":[{"id":"start","type":"START","parameters":{}},{"id":"repeat","type":"REPEAT","parameters":{"count":2},"children":[{"id":"turn","type":"TURN_LEFT","parameters":{"angleDegrees":90}},{"id":"wait","type":"WAIT","parameters":{"durationSeconds":1.5}}]},{"id":"end","type":"END","parameters":{}}]}""",
        )
        simulationPassService.recordSimulationPass(session.sessionId, 1)

        val result = executionRequestService.requestExecution(session.sessionId, 1)
        val restored = executionRepository.findById(result.executionId)

        assertEquals(
            """{"protocolVersion":1,"commands":[{"sequence":0,"sourceBlockId":"turn","type":"TURN","parameters":{"direction":"LEFT","angleDegrees":90.0}},{"sequence":1,"sourceBlockId":"wait","type":"WAIT","parameters":{"durationSeconds":1.5}},{"sequence":2,"sourceBlockId":"turn","type":"TURN","parameters":{"direction":"LEFT","angleDegrees":90.0}},{"sequence":3,"sourceBlockId":"wait","type":"WAIT","parameters":{"durationSeconds":1.5}}]}""",
            restored?.compiledCommandPayload,
        )
        assertEquals(setOf("COMMAND_TURN"), restored?.requiredCapabilities)
    }

    @Test
    fun `execution snapshot remains unchanged after a newer revision is created`() {
        val session = preparedSession()
        val result = executionRequestService.requestExecution(session.sessionId, 1)
        val before = requireNotNull(executionRepository.findById(result.executionId))

        sessionService.appendBlockRevision(session.sessionId, validBlockProgram("newer"))

        val after = requireNotNull(executionRepository.findById(result.executionId))
        assertEquals(2, sessionRepository.findById(session.sessionId)?.currentBlockVersion)
        assertEquals(before.compiledCommandPayload, after.compiledCommandPayload)
        assertEquals(before.requiredCapabilities, after.requiredCapabilities)
    }

    @Test
    fun `missing simulation pass is rejected`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, validBlockProgram())

        val exception = assertFailsWith<ApplicationException> {
            executionRequestService.requestExecution(session.sessionId, 1)
        }

        assertEquals(ErrorCode.SIMULATION_PASS_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `a pass for an older revision cannot create an execution for the changed program`() {
        val session = preparedSession()
        sessionService.appendBlockRevision(session.sessionId, validBlockProgram("changed"))

        val stale = assertFailsWith<ApplicationException> {
            executionRequestService.requestExecution(session.sessionId, 1)
        }
        val missingCurrentPass = assertFailsWith<ApplicationException> {
            executionRequestService.requestExecution(session.sessionId, 2)
        }

        assertEquals(ErrorCode.SIMULATION_BLOCK_VERSION_STALE, stale.errorCode)
        assertEquals(ErrorCode.SIMULATION_PASS_NOT_FOUND, missingCurrentPass.errorCode)
        assertNull(executionRepository.findActiveBySessionId(session.sessionId))
    }

    @Test
    fun `unknown session invalid stale future and missing revision are rejected`() {
        val unknown = assertFailsWith<ApplicationException> {
            executionRequestService.requestExecution(UUID.randomUUID(), 1)
        }
        assertEquals(ErrorCode.SESSION_NOT_FOUND, unknown.errorCode)

        val fresh = sessionService.createSession()
        val invalid = assertFailsWith<ApplicationException> {
            executionRequestService.requestExecution(fresh.sessionId, 0)
        }
        assertEquals(ErrorCode.SIMULATION_BLOCK_VERSION_INVALID, invalid.errorCode)

        sessionService.appendBlockRevision(fresh.sessionId, validBlockProgram("version-1"))
        sessionService.appendBlockRevision(fresh.sessionId, validBlockProgram("version-2"))
        val stale = assertFailsWith<ApplicationException> {
            executionRequestService.requestExecution(fresh.sessionId, 1)
        }
        val future = assertFailsWith<ApplicationException> {
            executionRequestService.requestExecution(fresh.sessionId, 3)
        }
        assertEquals(ErrorCode.SIMULATION_BLOCK_VERSION_STALE, stale.errorCode)
        assertEquals(ErrorCode.SIMULATION_BLOCK_VERSION_STALE, future.errorCode)

        val inconsistentSessionId = UUID.randomUUID()
        val transactionTemplate = TransactionTemplate(transactionManager)
        transactionTemplate.executeWithoutResult {
            sessionRepository.save(
                team.inreok.poppyserver.domain.session.model.Session.restore(
                    inconsistentSessionId,
                    1,
                    java.time.Instant.now(),
                ),
            )
        }
        val missingRevision = assertFailsWith<ApplicationException> {
            executionRequestService.requestExecution(inconsistentSessionId, 1)
        }
        assertEquals(ErrorCode.BLOCK_REVISION_NOT_FOUND, missingRevision.errorCode)
    }

    @Test
    @Transactional
    fun `active queued assigned and running executions block a new request`() {
        ExecutionStatus.entries.filter { it in ACTIVE_STATUSES }.forEach { activeStatus ->
            val session = preparedSession()
            val existing = executionRepository.save(Execution.create(session.sessionId, 1))
            when (activeStatus) {
                ExecutionStatus.QUEUED -> Unit
                ExecutionStatus.ASSIGNED -> existing.assign()
                ExecutionStatus.RUNNING -> {
                    existing.assign()
                    existing.start()
                }
                else -> error("active status required")
            }
            executionRepository.save(existing)

            val exception = assertFailsWith<ApplicationException> {
                executionRequestService.requestExecution(session.sessionId, 1)
            }
            assertEquals(ErrorCode.EXECUTION_SESSION_ACTIVE, exception.errorCode)
        }
    }

    @Test
    @Transactional
    fun `terminal executions do not block a new request`() {
        listOf(ExecutionStatus.COMPLETED, ExecutionStatus.FAILED, ExecutionStatus.CANCELLED).forEach { terminalStatus ->
            val session = preparedSession()
            val existing = Execution.create(session.sessionId, 1)
            when (terminalStatus) {
                ExecutionStatus.COMPLETED -> {
                    existing.assign()
                    existing.start()
                    existing.complete()
                }
                ExecutionStatus.FAILED -> {
                    existing.assign()
                    existing.start()
                    existing.fail()
                }
                ExecutionStatus.CANCELLED -> existing.cancel()
                else -> error("terminal status required")
            }
            executionRepository.save(existing)

            val result = executionRequestService.requestExecution(session.sessionId, 1)
            assertEquals(ExecutionStatus.QUEUED, result.status)
        }
    }

    @Test
    fun `concurrent requests create only one active execution`() {
        val session = preparedSession()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = (1..2).map {
                executor.submit<Result<ExecutionRequestResult>> {
                    ready.countDown()
                    assertTrue(start.await(10, TimeUnit.SECONDS))
                    runCatching { executionRequestService.requestExecution(session.sessionId, 1) }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, outcomes.count { it.isSuccess })
            assertEquals(1, outcomes.count { it.exceptionOrNull() is ApplicationException })
            assertNotNull(executionRepository.findActiveBySessionId(session.sessionId))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `transaction rollback leaves session and execution state consistent`() {
        val session = preparedSession()

        assertFailsWith<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                executionRequestService.requestExecution(session.sessionId, 1)
                throw IllegalStateException("rollback")
            }
        }

        assertEquals(1, sessionRepository.findById(session.sessionId)?.currentBlockVersion)
        assertNull(executionRepository.findActiveBySessionId(session.sessionId))
    }

    @Test
    fun `execution request HTTP API returns queued response`() {
        val session = preparedSession()

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{\"blockVersion\":1}"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.executionId").isNotEmpty)
            .andExpect(jsonPath("$.data.sessionId").value(session.sessionId.toString()))
            .andExpect(jsonPath("$.data.blockVersion").value(1))
            .andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andExpect(jsonPath("$.data.queuedAt").isNotEmpty)
            .andExpect(jsonPath("$.error").value(null))
    }

    @Test
    fun `execution request HTTP validation uses global error contract`() {
        val session = preparedSession()

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{\"blockVersion\":0}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("SIMULATION_BLOCK_VERSION_INVALID"))

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{\"blockVersion\":2}"),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("SIMULATION_BLOCK_VERSION_STALE"))

        mockMvc.perform(
            post("/api/v1/sessions/${UUID.randomUUID()}/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockVersion\":1}"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"))
    }

    private fun preparedSession(): SessionCreationResult {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, validBlockProgram())
        simulationPassService.recordSimulationPass(session.sessionId, 1)
        return session
    }

    companion object {
        private val ACTIVE_STATUSES = setOf(
            ExecutionStatus.QUEUED,
            ExecutionStatus.ASSIGNED,
            ExecutionStatus.RUNNING,
        )
    }
}
