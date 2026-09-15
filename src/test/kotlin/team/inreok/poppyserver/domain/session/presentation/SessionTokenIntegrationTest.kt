package team.inreok.poppyserver.domain.session.presentation

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.execution.application.ExecutionRequestService
import team.inreok.poppyserver.domain.session.application.SessionAccessVerifier
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
class SessionTokenIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var executionRequestService: ExecutionRequestService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var sessionRepository: SessionRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `Session create returns raw token once and persists only its digest`() {
        val first = sessionService.createSession()
        val second = sessionService.createSession()
        val firstDigest = jdbcTemplate.queryForObject(
            "SELECT session_token_digest FROM sessions WHERE id = ?",
            String::class.java,
            first.sessionId,
        )

        assertEquals(43, first.sessionToken.length)
        assertNotEquals(first.sessionToken, firstDigest)
        assertEquals(SessionAccessVerifier.digest(first.sessionToken), firstDigest)
        assertNotEquals(first.sessionToken, second.sessionToken)
    }

    @Test
    fun `Session create HTTP response includes a non-empty token`() {
        mockMvc.perform(
            post("/api/v1/sessions"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.sessionId").isNotEmpty)
            .andExpect(jsonPath("$.data.sessionToken").isNotEmpty)
            .andExpect(jsonPath("$.data.currentBlockVersion").value(0))
    }

    @Test
    fun `legacy Session without a token digest is restored as unauthenticated`() {
        val legacy = TransactionTemplate(transactionManager).execute {
            sessionRepository.save(Session.create(Instant.parse("2026-01-01T00:00:00Z")))
        }

        assertEquals(null, sessionRepository.findById(legacy.id)?.sessionTokenDigest)
    }

    @Test
    fun `valid token permits Session mutation while missing and invalid tokens are rejected`() {
        val session = sessionService.createSession()

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{\"document\":{}}"),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":{}}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", "invalid-token")
                .content("{\"document\":{}}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))
    }

    @Test
    fun `Session token cannot be used for another Session and unknown Session remains 404`() {
        val first = sessionService.createSession()
        val second = sessionService.createSession()

        mockMvc.perform(
            post("/api/v1/sessions/${second.sessionId}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", first.sessionToken)
                .content("{\"document\":{}}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))

        mockMvc.perform(
            post("/api/v1/sessions/${UUID.randomUUID()}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", "invalid-token")
                .content("{\"document\":{}}"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"))
    }

    @Test
    fun `Session token protects Simulation Pass and Execution Request`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, "{}")

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{\"blockVersion\":1}"),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{\"blockVersion\":1}"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("QUEUED"))

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", "invalid-token")
                .content("{\"blockVersion\":1}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))
    }

    @Test
    fun `cancel accepts owner token and rejects another Session token`() {
        val owner = preparedSession()
        val other = sessionService.createSession()
        val execution = executionRequestService.requestExecution(owner.sessionId, 1)

        mockMvc.perform(
            post("/api/v1/executions/${execution.executionId}/cancel")
                .header("X-Session-Token", other.sessionToken),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_ACCESS_DENIED"))

        mockMvc.perform(
            post("/api/v1/executions/${execution.executionId}/cancel")
                .header("X-Session-Token", owner.sessionToken),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))

        assertNotNull(jdbcTemplate.queryForObject(
            "SELECT finished_at FROM executions WHERE id = ?",
            java.sql.Timestamp::class.java,
            execution.executionId,
        ))
    }

    @Test
    fun `invalid token cannot cancel a Session Execution`() {
        val owner = preparedSession()
        val execution = executionRequestService.requestExecution(owner.sessionId, 1)

        mockMvc.perform(
            post("/api/v1/executions/${execution.executionId}/cancel")
                .header("X-Session-Token", "invalid-token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))
    }

    private fun preparedSession() = sessionService.createSession().also { session ->
        sessionService.appendBlockRevision(session.sessionId, "{}")
        val pass = mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{\"blockVersion\":1}"),
        ).andReturn()
        check(pass.response.status == 201)
    }
}
