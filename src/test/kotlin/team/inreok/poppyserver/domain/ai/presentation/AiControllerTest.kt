package team.inreok.poppyserver.domain.ai.presentation

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration",
        "poppy.ai.request-timeout=PT1S",
    ],
)
@AutoConfigureMockMvc
class AiControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun reset() {
        FakeInference.reset()
    }

    @Test
    fun `program draft returns validated steps from the inference service`() {
        FakeInference.draftBody = """{"steps":[{"kind":"repeat","count":4,"body":[{"kind":"moveForward","distanceM":1},{"kind":"rotateLeft","degrees":90}]},{"kind":"greet","extra":true}],"explanation":"네모를 그리고 인사하는 블록을 만들었어요."}"""

        draft("""{"utterance":"  네모를\t그리고 인사해 "}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.steps[0].kind").value("repeat"))
            .andExpect(jsonPath("$.data.steps[0].count").value(4))
            .andExpect(jsonPath("$.data.steps[0].body[0].distanceM").value(1))
            .andExpect(jsonPath("$.data.steps[0].body[1].degrees").value(90))
            .andExpect(jsonPath("$.data.steps[1].kind").value("greet"))
            .andExpect(jsonPath("$.data.steps[1].extra").doesNotExist())
            .andExpect(jsonPath("$.data.explanation").value("네모를 그리고 인사하는 블록을 만들었어요."))
        assertEquals(listOf("""{"utterance":"네모를 그리고 인사해"}"""), FakeInference.requests)
    }

    @Test
    fun `decimal distances are kept`() {
        FakeInference.draftBody = """{"steps":[{"kind":"move","distanceM":0.5}],"explanation":"뒤로"}"""

        draft("""{"utterance":"뒤로 50cm 가"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.steps[0].distanceM").value(0.5))
    }

    @Test
    fun `empty or too long utterance is rejected without calling inference`() {
        draft("""{"utterance":" \n "}""").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("AI_INPUT_INVALID"))
        draft("""{"utterance":"${"가".repeat(101)}"}""").andExpect(status().isBadRequest)
        draft("""{}""").andExpect(status().isBadRequest)
        assertTrue(FakeInference.requests.isEmpty())
    }

    @Test
    fun `inappropriate utterance is rejected`() {
        draft("""{"utterance":"씨발 앞으로 가"}""").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("AI_INPUT_REJECTED"))
        assertTrue(FakeInference.requests.isEmpty())
    }

    @Test
    fun `sit, nested repeat, out of range values and too many blocks become 503`() {
        val invalidSteps = listOf(
            """[{"kind":"sit"}]""",
            """[{"kind":"repeat","count":2,"body":[{"kind":"repeat","count":2,"body":[{"kind":"heart"}]}]}]""",
            """[{"kind":"moveForward","distanceM":3.5}]""",
            """[{"kind":"moveForward","distanceM":0.15}]""",
            """[{"kind":"rotateLeft","degrees":12.5}]""",
            """[{"kind":"repeat","count":3,"body":[]}]""",
            "[" + List(13) { """{"kind":"greet"}""" }.joinToString(",") + "]",
            "[]",
            "\"steps\"",
        )
        invalidSteps.forEach { steps ->
            FakeInference.draftBody = """{"steps":$steps,"explanation":"x"}"""
            draft("""{"utterance":"춤춰"}""")
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"))
        }
    }

    @Test
    fun `inference failure, invalid json and timeout become 503`() {
        FakeInference.status = 500
        draft("""{"utterance":"춤춰"}""").andExpect(status().isServiceUnavailable)

        FakeInference.status = 200
        FakeInference.draftBody = "not json"
        draft("""{"utterance":"춤춰"}""").andExpect(status().isServiceUnavailable)

        FakeInference.draftBody = """{"steps":[{"kind":"dance"}],"explanation":"춤"}"""
        FakeInference.delayMillis = 2_500
        val started = System.nanoTime()
        draft("""{"utterance":"춤춰"}""").andExpect(status().isServiceUnavailable)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_400)
    }

    @Test
    fun `inappropriate or missing explanation is replaced`() {
        FakeInference.draftBody = """{"steps":[{"kind":"dance"}],"explanation":"병신"}"""
        draft("""{"utterance":"춤춰"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.explanation").value("말한 내용으로 블록 초안을 만들었어요."))
    }

    @Test
    fun `hint is forwarded and filtered`() {
        FakeInference.hintBody = """{"message":"2번째 블록에서 로봇이 안전한 공간 밖으로 나가요."}"""
        hint("""{"programLines":["앞으로 3m 이동"],"violationCode":"exceeds-safe-zone","violationBlockNumber":2}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.message").value("2번째 블록에서 로봇이 안전한 공간 밖으로 나가요."))

        FakeInference.hintBody = """{"message":""}"""
        hint("""{"programLines":[]}""").andExpect(status().isServiceUnavailable)

        hint("""{"programLines":["${"가".repeat(101)}"]}""").andExpect(status().isBadRequest)
    }

    private fun draft(body: String) = mockMvc.perform(
        post("/api/v1/ai/program-drafts").contentType(MediaType.APPLICATION_JSON).content(body),
    )

    private fun hint(body: String) = mockMvc.perform(
        post("/api/v1/ai/hints").contentType(MediaType.APPLICATION_JSON).content(body),
    )

    object FakeInference {
        val requests = CopyOnWriteArrayList<String>()

        @Volatile var draftBody = ""

        @Volatile var hintBody = ""

        @Volatile var status = 200

        @Volatile var delayMillis = 0L

        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/program-drafts") { exchange ->
                requests += exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
                respond(exchange, draftBody)
            }
            createContext("/v1/hints") { exchange ->
                exchange.requestBody.readAllBytes()
                respond(exchange, hintBody)
            }
            executor = java.util.concurrent.Executors.newCachedThreadPool()
            start()
        }

        private fun respond(exchange: com.sun.net.httpserver.HttpExchange, body: String) {
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis)
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (ignored: Exception) {
                exchange.close()
            }
        }

        fun reset() {
            requests.clear()
            draftBody = ""
            hintBody = ""
            status = 200
            delayMillis = 0
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun inferenceUrl(registry: DynamicPropertyRegistry) {
            registry.add("poppy.ai.inference-url") { "http://127.0.0.1:${FakeInference.server.address.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            FakeInference.server.stop(0)
        }
    }
}
