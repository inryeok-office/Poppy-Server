package team.inreok.poppyserver.domain.agent.presentation

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import team.inreok.poppyserver.domain.agent.application.AgentRepository
import team.inreok.poppyserver.domain.robot.application.RegisterRobotCommand
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.application.RobotManagementService
import team.inreok.poppyserver.domain.robot.model.CapabilitySupportStatus
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotCapability
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AgentIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var agentRepository: AgentRepository

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Autowired
    lateinit var robotManagementService: RobotManagementService

    @Test
    fun `Agent를 등록하고 여러 Robot binding과 응답을 저장한다`() {
        val firstRobot = saveRobot("GO2", "EDU", "1.0.0", "MOVE")
        val secondRobot = saveRobot("GO2", "EDU", "1.0.1", "TURN")

        val response = registerAgent(
            agentName = "agent-${UUID.randomUUID()}",
            robots = listOf(
                registrationRobotJson(firstRobot, "1.0.0", "MOVE"),
                registrationRobotJson(secondRobot, "1.0.1", "TURN"),
            ),
        )

        assertEquals(201, response.status)
        val data = response.body["data"]
        val agentId = UUID.fromString(data["agentId"].asText())
        assertEquals(2, data["acceptedRobotIds"].size())
        assertEquals(agentId, agentRepository.findById(agentId)?.id)
        assertEquals(agentId, robotRepository.findById(firstRobot.id)?.agentId)
        assertEquals(agentId, robotRepository.findById(secondRobot.id)?.agentId)
    }

    @Test
    fun `Agent 등록은 token과 요청 형식을 검증한다`() {
        val body = registrationJson("agent-${UUID.randomUUID()}")

        mockMvc.perform(
            post("/api/v1/internal/agents/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))

        mockMvc.perform(
            post("/api/v1/internal/agents/register")
                .header("X-Agent-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))

        mockMvc.perform(
            post("/api/v1/internal/agents/register")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
    }

    @Test
    fun `동일 Agent 이름과 Robot binding 충돌을 거부한다`() {
        val agentName = "duplicate-${UUID.randomUUID()}"
        registerAgent(agentName)

        mockMvc.perform(
            post("/api/v1/internal/agents/register")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson(agentName)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("AGENT_ALREADY_REGISTERED"))

        val robot = saveRobot("GO2", "EDU", "1.0.0", "MOVE")
        registerAgent(
            agentName = "first-${UUID.randomUUID()}",
            robots = listOf(registrationRobotJson(robot, "1.0.0", "MOVE")),
        )

        mockMvc.perform(
            post("/api/v1/internal/agents/register")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    registrationJson(
                        "second-${UUID.randomUUID()}",
                        listOf(registrationRobotJson(robot, "1.0.0", "MOVE")),
                    ),
                ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("ROBOT_ALREADY_REGISTERED"))
    }

    @Test
    fun `존재하지 않는 Robot과 확인 가능한 호환성 불일치를 거부한다`() {
        mockMvc.perform(
            post("/api/v1/internal/agents/register")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    registrationJson(
                        "missing-robot-${UUID.randomUUID()}",
                        listOf(registrationRobotJson(UUID.randomUUID(), "1.0.0", "MOVE", model = "GO2", edition = "EDU")),
                    ),
                ),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"))

        val robot = saveRobot("GO2", "EDU", "1.0.0", "MOVE")
        mockMvc.perform(
            post("/api/v1/internal/agents/register")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    registrationJson(
                        "incompatible-${UUID.randomUUID()}",
                        listOf(registrationRobotJson(robot, "1.0.0", "MOVE", model = "GO2-A")),
                    ),
                ),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error.code").value("AGENT_COMPATIBILITY_INVALID"))
    }

    @Test
    fun `Heartbeat가 Agent와 Robot 상태 및 nullable reference를 갱신한다`() {
        val robot = saveRobot("GO2", "EDU", "1.0.0", "MOVE")
        val agentId = UUID.fromString(
            registerAgent(
                agentName = "heartbeat-${UUID.randomUUID()}",
                robots = listOf(registrationRobotJson(robot, "1.0.0", "MOVE")),
            ).body["data"]["agentId"].asText(),
        )
        val executionId = UUID.randomUUID()

        mockMvc.perform(
            post("/api/v1/internal/agents/$agentId/heartbeat")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    heartbeatJson(
                        robotId = robot.id,
                        connectionStatus = "ONLINE",
                        operationStatus = "READY",
                        batteryPercent = 75,
                        currentExecutionId = executionId,
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.agentId").value(agentId.toString()))
            .andExpect(jsonPath("$.data.acceptedAt").isNotEmpty)

        val savedRobot = robotRepository.findById(robot.id)
        assertNotNull(savedRobot)
        assertEquals(RobotConnectionStatus.ONLINE, savedRobot.connectionStatus)
        assertEquals(RobotOperationStatus.READY, savedRobot.operationStatus)
        assertEquals(75, savedRobot.batteryPercent)
        assertEquals(executionId, savedRobot.currentExecutionId)
        assertNotNull(savedRobot.lastHeartbeatAt)
        assertEquals(savedRobot.lastHeartbeatAt, agentRepository.findById(agentId)?.lastHeartbeatAt)

        mockMvc.perform(
            post("/api/v1/internal/agents/$agentId/heartbeat")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(heartbeatJson(robot.id, "OFFLINE", "UNAVAILABLE", null, null)),
        )
            .andExpect(status().isOk)

        val nullableRobot = robotRepository.findById(robot.id)
        assertNotNull(nullableRobot)
        assertEquals(null, nullableRobot.batteryPercent)
        assertEquals(null, nullableRobot.currentExecutionId)
    }

    @Test
    fun `Heartbeat는 Agent 존재·token·binding과 battery 범위를 검증한다`() {
        val robot = saveRobot("GO2", "EDU", "1.0.0", "MOVE")
        val agentId = UUID.fromString(
            registerAgent(
                "bound-${UUID.randomUUID()}",
                listOf(registrationRobotJson(robot, "1.0.0", "MOVE")),
            ).body["data"]["agentId"].asText(),
        )

        mockMvc.perform(
            post("/api/v1/internal/agents/${UUID.randomUUID()}/heartbeat")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(heartbeatJson(robot.id, "ONLINE", "READY", 50, null)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("AGENT_NOT_FOUND"))

        mockMvc.perform(
            post("/api/v1/internal/agents/$agentId/heartbeat")
                .header("X-Agent-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(heartbeatJson(robot.id, "ONLINE", "READY", 50, null)),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))

        val otherAgentId = UUID.fromString(
            registerAgent("unbound-${UUID.randomUUID()}").body["data"]["agentId"].asText(),
        )
        mockMvc.perform(
            post("/api/v1/internal/agents/$otherAgentId/heartbeat")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(heartbeatJson(robot.id, "ONLINE", "READY", 50, null)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("AGENT_ROBOT_BINDING_MISMATCH"))

        mockMvc.perform(
            post("/api/v1/internal/agents/$agentId/heartbeat")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(heartbeatJson(robot.id, "ONLINE", "READY", 101, null)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("HEARTBEAT_PAYLOAD_INVALID"))
    }

    private fun saveRobot(model: String, edition: String, firmwareVersion: String, capability: String): Robot =
        robotManagementService.register(
            RegisterRobotCommand(
                alias = "agent-test-${UUID.randomUUID()}",
                model = model,
                edition = edition,
                firmwareVersion = firmwareVersion,
                sdkVersion = "2.0.0",
                agentId = null,
                capabilities = listOf(RobotCapability(capability, CapabilitySupportStatus.UNVERIFIED)),
                safetyProfileId = null,
                isExternal = false,
            ),
        )

    private fun registerAgent(
        agentName: String,
        robots: List<String> = emptyList(),
    ): JsonResult {
        val result = mockMvc.perform(
            post("/api/v1/internal/agents/register")
                .header("X-Agent-Token", TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson(agentName, robots)),
        ).andReturn()
        return JsonResult(result.response.status, objectMapper.readTree(result.response.contentAsString))
    }

    private fun registrationJson(agentName: String, robots: List<String> = emptyList()): String = """
        {
          "agentName":"$agentName",
          "agentVersion":"1.0.0",
          "sdkVersion":"2.0.0",
          "platform":"linux-arm64",
          "robots":[${robots.joinToString(",")}]
        }
    """.trimIndent()

    private fun registrationRobotJson(
        robot: Robot,
        firmwareVersion: String,
        capability: String,
        model: String = robot.model,
        edition: String = robot.edition,
    ): String = registrationRobotJson(robot.id, firmwareVersion, capability, model, edition)

    private fun registrationRobotJson(
        robotId: UUID,
        firmwareVersion: String,
        capability: String,
        model: String,
        edition: String,
    ): String = """
        {
          "robotId":"$robotId",
          "model":"$model",
          "edition":"$edition",
          "firmwareVersion":"$firmwareVersion",
          "capabilities":["$capability"]
        }
    """.trimIndent()

    private fun heartbeatJson(
        robotId: UUID,
        connectionStatus: String,
        operationStatus: String,
        batteryPercent: Int?,
        currentExecutionId: UUID?,
    ): String = """
        {
          "sentAt":"2026-08-25T10:20:30",
          "robots":[{
            "robotId":"$robotId",
            "connectionStatus":"$connectionStatus",
            "operationalStatus":"$operationStatus",
            "batteryPercent":${batteryPercent ?: "null"},
            "currentExecutionId":${currentExecutionId?.let { "\"$it\"" } ?: "null"}
          }]
        }
    """.trimIndent()

    private data class JsonResult(val status: Int, val body: JsonNode)

    companion object {
        private const val TEST_TOKEN = "test-agent-token"

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("poppy.agent.token") { TEST_TOKEN }
        }
    }
}
