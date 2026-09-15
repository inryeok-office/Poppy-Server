package team.inreok.poppyserver.domain.execution.presentation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import team.inreok.poppyserver.domain.execution.application.ExecutionSseService
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
class ExecutionSseIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var executionSseService: ExecutionSseService

    @Autowired
    lateinit var sessionService: SessionService

    @Test
    fun `valid session token creates an emitter`() {
        val session = sessionService.createSession()

        executionSseService.subscribe(session.sessionId, session.sessionToken).complete()
    }

    @Test
    fun `missing or invalid session token is rejected`() {
        val session = sessionService.createSession()

        assertFailsWith<ApplicationException> {
            executionSseService.subscribe(session.sessionId, null)
        }.also { assertEquals("SESSION_TOKEN_INVALID", it.errorCode.code) }

        assertFailsWith<ApplicationException> {
            executionSseService.subscribe(session.sessionId, "invalid-token")
        }.also { assertEquals("SESSION_TOKEN_INVALID", it.errorCode.code) }
    }

    @Test
    fun `another session token cannot subscribe`() {
        val session = sessionService.createSession()
        val other = sessionService.createSession()

        assertFailsWith<ApplicationException> {
            executionSseService.subscribe(session.sessionId, other.sessionToken)
        }.also { assertEquals("SESSION_TOKEN_INVALID", it.errorCode.code) }
    }
}
