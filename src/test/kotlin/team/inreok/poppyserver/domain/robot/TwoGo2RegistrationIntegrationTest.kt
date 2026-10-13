package team.inreok.poppyserver.domain.robot

import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import team.inreok.poppyserver.support.AdminSessionCookieTestConfiguration
import team.inreok.poppyserver.support.AdminSessionTestSupport
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminSessionCookieTestConfiguration::class)
class TwoGo2RegistrationIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun openAdminSession() {
        AdminSessionTestSupport.insertValidSession(jdbcTemplate)
    }

    @Test
    fun `두 GO2 Robot은 서로 다른 UUID로 등록되고 각 Agent에 독립적으로 연결된다`() {
        val firstRobot = registerRobot("go2-edu-01-${UUID.randomUUID()}")
        val secondRobot = registerRobot("go2-edu-02-${UUID.randomUUID()}")
        val firstRobotId = UUID.fromString(firstRobot["data"]["robotId"].stringValue())
        val secondRobotId = UUID.fromString(secondRobot["data"]["robotId"].stringValue())

        assertNotEquals(firstRobotId, secondRobotId)

        mockMvc.perform(get("/api/v1/admin/robots"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.robots[?(@.robotId == '$firstRobotId')]").exists())
            .andExpect(jsonPath("$.data.robots[?(@.robotId == '$secondRobotId')]").exists())

        registerAgent("go2-agent-01-${UUID.randomUUID()}", firstRobotId)
            .andExpect(status().isCreated)
        registerAgent("go2-agent-02-${UUID.randomUUID()}", secondRobotId)
            .andExpect(status().isCreated)
        registerAgent("conflict-${UUID.randomUUID()}", firstRobotId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("ROBOT_ALREADY_REGISTERED"))
    }

    @Test
    fun `Agent 등록은 edition firmware capability 불일치를 거부한다`() {
        val editionRobot = robotId(registerRobot("edition-${UUID.randomUUID()}"))
        registerAgent("edition-${UUID.randomUUID()}", editionRobot, edition = "PRO")
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.error.code").value("AGENT_COMPATIBILITY_INVALID"))

        val firmwareRobot = robotId(registerRobot("firmware-${UUID.randomUUID()}"))
        registerAgent("firmware-${UUID.randomUUID()}", firmwareRobot, firmwareVersion = "different")
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.error.code").value("AGENT_COMPATIBILITY_INVALID"))

        val capabilityRobot = robotId(registerRobot("capability-${UUID.randomUUID()}"))
        registerAgent("capability-${UUID.randomUUID()}", capabilityRobot, capabilities = "[\"TURN\"]")
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.error.code").value("AGENT_COMPATIBILITY_INVALID"))
    }

    private fun registerRobot(alias: String): JsonNode {
        val result = mockMvc.perform(
            post("/api/v1/admin/robots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "alias":"$alias",
                      "model":"GO2",
                      "edition":"EDU",
                      "firmwareVersion":"test-firmware",
                      "sdkVersion":"test-sdk",
                      "capabilities":[{"code":"MOVE"}],
                      "isExternal":false
                    }
                    """.trimIndent(),
                )
                .with(csrf().asHeader()),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString)
    }

    private fun robotId(response: JsonNode): UUID = UUID.fromString(response["data"]["robotId"].stringValue())

    private fun registerAgent(
        agentName: String,
        robotId: UUID,
        edition: String = "EDU",
        firmwareVersion: String = "test-firmware",
        capabilities: String = "[\"MOVE\"]",
    ) = mockMvc.perform(
        post("/api/v1/internal/agents/register")
            .header("X-Agent-Token", TEST_BOOTSTRAP_TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "agentName":"$agentName",
                  "agentVersion":"test-agent-version",
                  "sdkVersion":"test-agent-sdk",
                  "platform":"test-platform",
                  "robots":[
                    {
                      "robotId":"$robotId",
                      "model":"GO2",
                      "edition":"$edition",
                      "firmwareVersion":"$firmwareVersion",
                      "capabilities":$capabilities
                    }
                  ]
                }
                """.trimIndent(),
            ),
    )

    companion object {
        private const val TEST_BOOTSTRAP_TOKEN = "test-bootstrap-token"

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("poppy.agent.token") { TEST_BOOTSTRAP_TOKEN }
        }
    }
}
