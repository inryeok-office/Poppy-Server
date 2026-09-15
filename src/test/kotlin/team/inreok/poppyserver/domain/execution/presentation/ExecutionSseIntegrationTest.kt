package team.inreok.poppyserver.domain.execution.presentation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.domain.execution.application.ExecutionRequestService
import team.inreok.poppyserver.domain.execution.application.ExecutionSseService
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.domain.session.application.SimulationPassService
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import team.inreok.poppyserver.support.validBlockProgram
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ExecutionSseIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var executionSseService: ExecutionSseService

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var executionRequestService: ExecutionRequestService

    @Autowired
    lateinit var simulationPassService: SimulationPassService

    @Test
    fun `execution status changes are sent to an existing session stream`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, validBlockProgram())
        simulationPassService.recordSimulationPass(session.sessionId, 1)

        val stream = mockMvc.perform(
            get("/api/v1/sessions/${session.sessionId}/events")
                .header("X-Session-Token", session.sessionToken)
                .accept(MediaType.TEXT_EVENT_STREAM),
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        val execution = executionRequestService.requestExecution(session.sessionId, 1)
        repeat(20) {
            if (stream.response.contentAsString.contains(execution.executionId.toString())) {
                return@repeat
            }
            Thread.sleep(50)
        }

        assertTrue(stream.response.contentAsString.contains("execution-status"))
        assertTrue(stream.response.contentAsString.contains(execution.executionId.toString()))
    }

    @Test
    fun `missing session token returns json unauthorized response`() {
        val session = sessionService.createSession()

        mockMvc.perform(get("/api/v1/sessions/${session.sessionId}/events"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))

        mockMvc.perform(
            get("/api/v1/sessions/${session.sessionId}/events")
                .header("X-Session-Token", "invalid-token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))
    }

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
