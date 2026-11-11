package team.inreok.poppyserver.domain.execution.presentation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 이벤트가 없어도 구독 중인 SSE 연결에 주기적으로 주석 줄(`:keepalive`)이 간다.
 * 프록시(Next.js 재작성 기본 30초)가 유휴 연결을 끊는 것을 막기 위한 것이다.
 */
@SpringBootTest(properties = ["poppy.execution.sse-heartbeat-milliseconds=100"])
@AutoConfigureMockMvc
class ExecutionSseHeartbeatIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionService: SessionService

    @Test
    fun `an idle session stream receives keepalive comments`() {
        val session = sessionService.createSession()
        val stream = mockMvc.perform(
            get("/api/v1/sessions/${session.sessionId}/events")
                .header("X-Session-Token", session.sessionToken)
                .accept(MediaType.TEXT_EVENT_STREAM),
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        val deadline = System.currentTimeMillis() + 5_000
        while (!stream.response.contentAsString.contains(":keepalive") && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        val body = stream.response.contentAsString
        assertTrue(body.contains(":keepalive"), "no heartbeat received: '$body'")
        // 주석 줄이라 클라이언트가 처리할 이벤트(event:/data:)가 아니다.
        assertFalse(body.contains("event:"))
        assertFalse(body.contains("data:"))
    }
}
