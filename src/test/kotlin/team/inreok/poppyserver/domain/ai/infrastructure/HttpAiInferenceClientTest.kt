package team.inreok.poppyserver.domain.ai.infrastructure

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import team.inreok.poppyserver.domain.ai.application.AiInferenceUnavailableException
import tools.jackson.databind.json.JsonMapper
import java.net.ServerSocket
import java.time.Duration

class HttpAiInferenceClientTest {
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `missing inference url is unavailable`() {
        val client = HttpAiInferenceClient(mapper, " ", Duration.ofSeconds(1), Duration.ofSeconds(1))
        assertThrows<AiInferenceUnavailableException> { client.requestProgramDraft("춤춰") }
    }

    @Test
    fun `connection refused is unavailable`() {
        val port = ServerSocket(0).use { it.localPort }
        val client = HttpAiInferenceClient(mapper, "http://127.0.0.1:$port/", Duration.ofSeconds(1), Duration.ofSeconds(1))
        assertThrows<AiInferenceUnavailableException> { client.requestProgramDraft("춤춰") }
    }
}
