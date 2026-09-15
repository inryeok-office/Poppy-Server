package team.inreok.poppyserver.domain.agent.presentation

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
class AgentPrincipalIsolationIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `Agent credential는 heartbeat delivery status의 path principal을 제한한다`() {
        val agentA = registerAgent("principal-a-${UUID.randomUUID()}")
        val agentB = registerAgent("principal-b-${UUID.randomUUID()}")

        heartbeat(agentA.id, agentA.token)
            .andExpect(status().isOk)

        heartbeat(agentB.id, agentA.token)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AGENT_PRINCIPAL_MISMATCH"))

        mockMvc.perform(
            get("/api/v1/internal/agents/${agentB.id}/executions/next")
                .param("robotId", UUID.randomUUID().toString())
                .header("X-Agent-Token", agentA.token),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AGENT_PRINCIPAL_MISMATCH"))

        mockMvc.perform(
            post("/api/v1/internal/agents/${agentB.id}/executions/${UUID.randomUUID()}/status")
                .header("X-Agent-Token", agentA.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"robotId\":\"${UUID.randomUUID()}\",\"status\":\"RUNNING\"}"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AGENT_PRINCIPAL_MISMATCH"))

        heartbeat(agentA.id, "wrong-token")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))

        heartbeat(agentA.id, null)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))
    }

    @Test
    fun `same agent re-registration은 기존 credential을 즉시 폐기한다`() {
        val agentName = "principal-rotation-${UUID.randomUUID()}"
        val first = registerAgent(agentName)
        val second = registerAgent(agentName)

        assertNotEquals(first.token, second.token)
        heartbeat(first.id, first.token)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))
        heartbeat(second.id, second.token)
            .andExpect(status().isOk)
    }

    private fun registerAgent(name: String): RegisteredAgent {
        val result = mockMvc.perform(
            post("/api/v1/internal/agents/register")
                .header("X-Agent-Token", BOOTSTRAP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "agentName": "$name",
                      "agentVersion": "1.0.0",
                      "sdkVersion": "2.0.0",
                      "platform": "test",
                      "robots": []
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn()
        val data = objectMapper.readTree(result.response.contentAsString).get("data")
        return RegisteredAgent(
            id = UUID.fromString(data.get("agentId").asText()),
            token = data.get("agentToken").asText(),
        )
    }

    private fun heartbeat(agentId: UUID, token: String?) = mockMvc.perform(
        post("/api/v1/internal/agents/$agentId/heartbeat")
            .apply { if (token != null) header("X-Agent-Token", token) }
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sentAt\":\"2026-09-15T00:00:00\",\"robots\":[]}"),
    )

    private data class RegisteredAgent(val id: UUID, val token: String)

    companion object {
        private const val BOOTSTRAP_TOKEN = "test-agent-token"

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("poppy.agent.token") { BOOTSTRAP_TOKEN }
        }
    }
}
