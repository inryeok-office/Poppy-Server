package team.inreok.poppyserver.domain.session.presentation

import java.time.Instant
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
import team.inreok.poppyserver.domain.session.application.RecoveryCodeGenerator
import team.inreok.poppyserver.domain.session.application.SessionAccessVerifier
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

@SpringBootTest
@AutoConfigureMockMvc
class SessionRecoveryIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var sessionAccessVerifier: SessionAccessVerifier

    @Autowired
    lateinit var sessionRepository: SessionRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `Session creation returns recovery code and persists only its digest`() {
        val created = sessionService.createSession()
        val digest = jdbcTemplate.queryForObject(
            "SELECT recovery_code_digest FROM sessions WHERE id = ?",
            String::class.java,
            created.sessionId,
        )

        assertEquals(9, created.recoveryCode.length)
        assertEquals('-', created.recoveryCode[4])
        assertNotEquals(created.recoveryCode, digest)
        assertEquals(RecoveryCodeGenerator.digest(created.recoveryCode), digest)
    }

    @Test
    fun `valid recovery keeps Session data and rotates the session token`() {
        val created = sessionService.createSession()
        sessionService.appendBlockRevision(created.sessionId, "{}")

        val result = mockMvc.perform(
            post("/api/v1/sessions/restore")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recoveryCode\":\"${created.recoveryCode}\"}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sessionId").value(created.sessionId.toString()))
            .andExpect(jsonPath("$.data.sessionToken").isNotEmpty)
            .andExpect(jsonPath("$.data.currentBlockVersion").value(1))
            .andReturn()
        val newToken = result.response.contentAsString.substringAfter("sessionToken\":\"").substringBefore("\"")

        assertNotEquals(created.sessionToken, newToken)
        assertFailsWith<ApplicationException> { sessionAccessVerifier.authenticate(created.sessionToken) }
            .also { assertEquals("SESSION_TOKEN_INVALID", it.errorCode.code) }
        assertEquals(created.sessionId, sessionAccessVerifier.authenticate(newToken).id)
        assertEquals(1, sessionRepository.findById(created.sessionId)?.currentBlockVersion)
    }

    @Test
    fun `malformed recovery code is rejected`() {
        mockMvc.perform(
            post("/api/v1/sessions/restore")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recoveryCode\":\"bad\"}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("RECOVERY_CODE_INVALID"))
    }

    @Test
    fun `unknown recovery code is not found and repeated attempts are rate limited`() {
        repeat(5) {
            mockMvc.perform(
                post("/api/v1/sessions/restore")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"recoveryCode\":\"ABCD-2345\"}")
                    .with { request -> request.apply { remoteAddr = "198.51.100.10" } },
            ).andExpect(status().isNotFound)
        }

        mockMvc.perform(
            post("/api/v1/sessions/restore")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recoveryCode\":\"ABCD-2345\"}")
                .with { request -> request.apply { remoteAddr = "198.51.100.10" } },
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("RECOVERY_ATTEMPT_RATE_LIMITED"))
    }

    @Test
    fun `expired Session cannot be restored`() {
        val created = sessionService.createSession()
        val stored = sessionRepository.findById(created.sessionId)!!
        stored.expire(Instant.parse("2026-09-15T00:00:00Z"))
        TransactionTemplate(transactionManager).executeWithoutResult {
            sessionRepository.save(stored)
        }

        mockMvc.perform(
            post("/api/v1/sessions/restore")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recoveryCode\":\"${created.recoveryCode}\"}"),
        )
            .andExpect(status().isGone)
            .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"))
    }

    @Test
    fun `legacy Session without recovery code cannot be restored`() {
        val legacy = TransactionTemplate(transactionManager).execute {
            sessionRepository.save(Session.create(Instant.parse("2026-01-01T00:00:00Z")))
        }

        assertNotNull(legacy)
        mockMvc.perform(
            post("/api/v1/sessions/restore")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recoveryCode\":\"ABCD-2345\"}"),
        ).andExpect(status().isNotFound)
    }
}
