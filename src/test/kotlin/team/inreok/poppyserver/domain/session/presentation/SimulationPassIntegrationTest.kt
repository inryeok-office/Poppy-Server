package team.inreok.poppyserver.domain.session.presentation

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.MediaType
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.domain.session.application.SimulationPassRecordResult
import team.inreok.poppyserver.domain.session.application.SimulationPassRepository
import team.inreok.poppyserver.domain.session.application.SimulationPassService
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class SimulationPassIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var simulationPassService: SimulationPassService

    @Autowired
    lateinit var sessionRepository: SessionRepository

    @Autowired
    lateinit var simulationPassRepository: SimulationPassRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `current revision simulation pass is persisted with passedAt`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"blocks\":[]}")

        val result = simulationPassService.recordSimulationPass(session.sessionId, 1)

        assertEquals(session.sessionId, result.sessionId)
        assertEquals(1, result.blockVersion)
        assertNotNull(result.passedAt)
        assertEquals(result.passedAt, simulationPassRepository.findById(session.sessionId, 1)?.passedAt)
    }

    @Test
    fun `duplicate pass is idempotent and keeps the original timestamp`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"version\":1}")

        val first = simulationPassService.recordSimulationPass(session.sessionId, 1)
        val second = simulationPassService.recordSimulationPass(session.sessionId, 1)

        assertTrue(first.created)
        assertTrue(!second.created)
        assertEquals(first.sessionId, second.sessionId)
        assertEquals(first.blockVersion, second.blockVersion)
        assertEquals(first.passedAt, second.passedAt)
        assertEquals(
            1L,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM simulation_passes WHERE session_id = ? AND block_version = ?",
                Long::class.java,
                session.sessionId,
                1L,
            ),
        )
    }

    @Test
    fun `database primary key prevents duplicate pass rows`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"version\":1}")
        simulationPassService.recordSimulationPass(session.sessionId, 1)

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                "INSERT INTO simulation_passes (session_id, block_version, passed_at) VALUES (?, ?, ?)",
                session.sessionId,
                1L,
                java.sql.Timestamp.from(java.time.Instant.now()),
            )
        }
    }

    @Test
    fun `zero version is rejected for a new session`() {
        val session = sessionService.createSession()

        val exception = assertFailsWith<ApplicationException> {
            simulationPassService.recordSimulationPass(session.sessionId, 0)
        }

        assertEquals(ErrorCode.SIMULATION_BLOCK_VERSION_INVALID, exception.errorCode)
    }

    @Test
    fun `unknown session is rejected`() {
        val exception = assertFailsWith<ApplicationException> {
            simulationPassService.recordSimulationPass(UUID.randomUUID(), 1)
        }

        assertEquals(ErrorCode.SESSION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `current version without a revision is rejected`() {
        val sessionId = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO sessions (id, current_block_version, created_at) VALUES (?, ?, ?)",
            sessionId,
            1L,
            java.sql.Timestamp.from(java.time.Instant.now()),
        )

        val exception = assertFailsWith<ApplicationException> {
            simulationPassService.recordSimulationPass(sessionId, 1)
        }

        assertEquals(ErrorCode.BLOCK_REVISION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `stale and future versions are rejected`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"version\":1}")
        sessionService.appendBlockRevision(session.sessionId, "{\"version\":2}")

        val stale = assertFailsWith<ApplicationException> {
            simulationPassService.recordSimulationPass(session.sessionId, 1)
        }
        val future = assertFailsWith<ApplicationException> {
            simulationPassService.recordSimulationPass(session.sessionId, 3)
        }

        assertEquals(ErrorCode.SIMULATION_BLOCK_VERSION_STALE, stale.errorCode)
        assertEquals(ErrorCode.SIMULATION_BLOCK_VERSION_STALE, future.errorCode)
    }

    @Test
    fun `same version passes are independent across sessions`() {
        val firstSession = sessionService.createSession()
        val secondSession = sessionService.createSession()
        sessionService.appendBlockRevision(firstSession.sessionId, "{\"session\":1}")
        sessionService.appendBlockRevision(secondSession.sessionId, "{\"session\":2}")

        val first = simulationPassService.recordSimulationPass(firstSession.sessionId, 1)
        val second = simulationPassService.recordSimulationPass(secondSession.sessionId, 1)

        assertNotEquals(first.sessionId, second.sessionId)
        assertEquals(
            2L,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM simulation_passes WHERE session_id IN (?, ?)",
                Long::class.java,
                firstSession.sessionId,
                secondSession.sessionId,
            ),
        )
    }

    @Test
    fun `old pass remains after a new revision but is not current`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"version\":1}")
        val firstPass = simulationPassService.recordSimulationPass(session.sessionId, 1)

        sessionService.appendBlockRevision(session.sessionId, "{\"version\":2}")

        assertNotNull(simulationPassRepository.findById(session.sessionId, 1))
        assertNotEquals(sessionRepository.findById(session.sessionId)?.currentBlockVersion, firstPass.blockVersion)
        val secondPass = simulationPassService.recordSimulationPass(session.sessionId, 2)
        assertEquals(2, secondPass.blockVersion)
    }

    @Test
    fun `transaction rollback does not persist a simulation pass`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"version\":1}")

        assertFailsWith<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                simulationPassService.recordSimulationPass(session.sessionId, 1)
                throw IllegalStateException("rollback")
            }
        }

        assertNull(simulationPassRepository.findById(session.sessionId, 1))
        assertEquals(1, sessionRepository.findById(session.sessionId)?.currentBlockVersion)
    }

    @Test
    fun `concurrent pass requests create one immutable row`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"version\":1}")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = (1..2).map {
                executor.submit<SimulationPassRecordResult> {
                    ready.countDown()
                    assertTrue(start.await(10, TimeUnit.SECONDS))
                    simulationPassService.recordSimulationPass(session.sessionId, 1)
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(results[0].sessionId, results[1].sessionId)
            assertEquals(results[0].blockVersion, results[1].blockVersion)
            assertEquals(results[0].passedAt, results[1].passedAt)
            assertEquals(1, results.count { it.created })
            assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM simulation_passes WHERE session_id = ? AND block_version = ?",
                    Long::class.java,
                    session.sessionId,
                    1L,
                ),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `pass and revision append are serialized by the session lock`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"version\":1}")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val passFuture = executor.submit<Result<SimulationPassRecordResult>> {
                ready.countDown()
                assertTrue(start.await(10, TimeUnit.SECONDS))
                runCatching { simulationPassService.recordSimulationPass(session.sessionId, 1) }
            }
            val appendFuture = executor.submit<Result<Long>> {
                ready.countDown()
                assertTrue(start.await(10, TimeUnit.SECONDS))
                runCatching {
                    sessionService.appendBlockRevision(session.sessionId, "{\"version\":2}").blockVersion
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            passFuture.get(30, TimeUnit.SECONDS)
            appendFuture.get(30, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertEquals(2, sessionRepository.findById(session.sessionId)?.currentBlockVersion)
        val pass = simulationPassRepository.findById(session.sessionId, 1)
        assertTrue(pass == null || pass.blockVersion != sessionRepository.findById(session.sessionId)!!.currentBlockVersion)
    }

    @Test
    fun `simulation pass HTTP API returns envelope and preserves duplicate`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"blocks\":[]}")

        val first = mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockVersion\":1}"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sessionId").value(session.sessionId.toString()))
            .andExpect(jsonPath("$.data.blockVersion").value(1))
            .andExpect(jsonPath("$.data.passedAt").isNotEmpty)
            .andExpect(jsonPath("$.error").value(null))
            .andReturn()
            .response
            .contentAsString

        val second = mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockVersion\":1}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.blockVersion").value(1))
            .andReturn()
            .response
            .contentAsString

        assertEquals(first, second)
    }

    @Test
    fun `simulation pass HTTP validation and unknown session use global errors`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{\"blocks\":[]}")

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockVersion\":}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockVersion\":0}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("SIMULATION_BLOCK_VERSION_INVALID"))

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockVersion\":2}"),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("SIMULATION_BLOCK_VERSION_STALE"))

        mockMvc.perform(
            post("/api/v1/sessions/${UUID.randomUUID()}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockVersion\":1}"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"))
    }
}
