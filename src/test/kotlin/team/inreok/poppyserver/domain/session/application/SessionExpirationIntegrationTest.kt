package team.inreok.poppyserver.domain.session.application

import java.time.Instant
import java.util.UUID
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
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class SessionExpirationIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var sessionRepository: SessionRepository

    @Autowired
    lateinit var sessionAccessVerifier: SessionAccessVerifier

    @Autowired
    lateinit var sessionExpirationService: SessionExpirationService

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `new session starts activity at creation`() {
        val created = sessionService.createSession()

        val session = sessionRepository.findById(created.sessionId)
        assertNotNull(session)
        assertEquals(session.createdAt, session.lastActivityAt)
        assertEquals(null, session.expiredAt)
    }

    @Test
    fun `successful token verification updates activity`() {
        val created = sessionService.createSession()
        val before = sessionRepository.findById(created.sessionId)!!.lastActivityAt

        sessionAccessVerifier.verify(created.sessionId, created.sessionToken)

        val after = sessionRepository.findById(created.sessionId)!!.lastActivityAt
        assertTrue(after >= before)
    }

    @Test
    fun `invalid token does not update activity`() {
        val created = sessionService.createSession()
        val before = sessionRepository.findById(created.sessionId)!!.lastActivityAt

        assertFailsWith<ApplicationException> {
            sessionAccessVerifier.verify(created.sessionId, "invalid-token")
        }

        assertEquals(before, sessionRepository.findById(created.sessionId)!!.lastActivityAt)
    }

    @Test
    fun `cross session ownership failure does not update authenticated session activity`() {
        val owner = sessionService.createSession()
        val other = sessionService.createSession()
        val before = sessionRepository.findById(other.sessionId)!!.lastActivityAt

        assertFailsWith<ApplicationException> {
            sessionAccessVerifier.verifyOwnership(owner.sessionId, other.sessionToken)
        }

        assertEquals(before, sessionRepository.findById(other.sessionId)!!.lastActivityAt)
    }

    @Test
    fun `inactive session expires and blocks further session access`() {
        val now = Instant.parse("2026-09-15T00:00:00Z")
        val issued = sessionAccessVerifier.issue()
        val sessionId = UUID.randomUUID()
        saveSession(
            Session.createWithToken(
                sessionTokenDigest = issued.digest,
                createdAt = now.minusSeconds(3601),
            ).let { created ->
                Session.restore(
                    id = sessionId,
                    currentBlockVersion = created.currentBlockVersion,
                    createdAt = created.createdAt,
                    sessionTokenDigest = created.sessionTokenDigest,
                    lastActivityAt = now.minusSeconds(3601),
                )
            },
        )

        assertEquals(1, sessionExpirationService.expireInactiveSessions(now))

        val expired = sessionRepository.findById(sessionId)
        assertNotNull(expired)
        assertNotNull(expired.expiredAt)
        assertFailsWith<ApplicationException> {
            sessionAccessVerifier.verify(sessionId, issued.raw)
        }.also { assertEquals("SESSION_EXPIRED", it.errorCode.code) }

        mockMvc.perform(
            post("/api/v1/sessions/$sessionId/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", issued.raw)
                .content("{\"document\":{\"blocks\":[]}}"),
        )
            .andExpect(status().isGone)
            .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"))
    }

    @Test
    fun `expiration is idempotent`() {
        val now = Instant.parse("2026-09-15T00:00:00Z")
        val session = Session.restore(
            id = UUID.randomUUID(),
            currentBlockVersion = 0,
            createdAt = now.minusSeconds(3601),
            lastActivityAt = now.minusSeconds(3601),
        )
        saveSession(session)

        assertEquals(1, sessionExpirationService.expireInactiveSessions(now))
        val expiredAt = sessionRepository.findById(session.id)!!.expiredAt
        assertEquals(0, sessionExpirationService.expireInactiveSessions(now.plusSeconds(60)))
        assertEquals(expiredAt, sessionRepository.findById(session.id)!!.expiredAt)
    }

    private fun saveSession(session: Session) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            sessionRepository.save(session)
        }
    }
}
