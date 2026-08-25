package team.inreok.poppyserver.domain.robot

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.domain.robot.infrastructure.RobotJpaRepository
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class RobotManagementIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var robotJpaRepository: RobotJpaRepository

    @Test
    fun `Robot을 등록하고 명시한 capability와 nullable 참조를 저장한다`() {
        val agentId = UUID.randomUUID()
        val safetyProfileId = UUID.randomUUID()
        val response = register(
            alias = "등록-로봇-${UUID.randomUUID()}",
            agentId = agentId,
            safetyProfileId = safetyProfileId,
            capabilities = "[{\"code\":\"MOVE\",\"status\":\"UNVERIFIED\"}]",
            isExternal = true,
        )

        assertEquals("OFFLINE", response["data"]["connectionStatus"].asText())
        assertEquals("UNAVAILABLE", response["data"]["operationalStatus"].asText())
        assertEquals(1, response["data"]["capabilities"].size())
        assertEquals("MOVE", response["data"]["capabilities"][0]["code"].asText())
        val robotId = UUID.fromString(response["data"]["robotId"].asText())
        val entity = robotJpaRepository.findById(robotId).orElseThrow()
        assertEquals(agentId, entity.agentId)
        assertEquals(safetyProfileId, entity.safetyProfileId)
        assertEquals(true, entity.isExternal)
        assertEquals("MOVE", entity.capabilities.single().code)

        val nullableRobotId = UUID.fromString(register(alias = "nullable-${UUID.randomUUID()}")["data"]["robotId"].asText())
        val nullableEntity = robotJpaRepository.findById(nullableRobotId).orElseThrow()
        assertEquals(null, nullableEntity.agentId)
        assertEquals(null, nullableEntity.safetyProfileId)
    }

    @Test
    fun `등록 요청의 형식 오류와 동일 Agent 중복을 검증한다`() {
        mockMvc.perform(
            post("/api/v1/admin/robots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))

        val agentId = UUID.randomUUID()
        register(alias = "중복-원본-${UUID.randomUUID()}", agentId = agentId)
        mockMvc.perform(
            post("/api/v1/admin/robots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson("중복-대상-${UUID.randomUUID()}", agentId = agentId)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("ROBOT_ALREADY_REGISTERED"))
    }

    @Test
    fun `Robot 목록은 connectionStatus와 operational status filter를 적용한다`() {
        val alias = "목록-로봇-${UUID.randomUUID()}"
        val id = register(alias = alias)["data"]["robotId"].asText()

        mockMvc.perform(get("/api/v1/admin/robots?connectionStatus=OFFLINE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.robots[?(@.robotId == '$id')].occupied").value(false))
            .andExpect(jsonPath("$.data.robots[?(@.robotId == '$id')].currentExecutionId").value(null))
            .andExpect(jsonPath("$.data.robots[?(@.robotId == '$id')].lastHeartbeatAt").value(null))

        mockMvc.perform(
            patch("/api/v1/admin/robots/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(alias, operationStatus = "READY")),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/admin/robots?status=READY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.robots[?(@.robotId == '$id')].operationalStatus").value("READY"))
    }

    @Test
    fun `잘못된 filter는 Robot filter 오류를 반환한다`() {
        mockMvc.perform(get("/api/v1/admin/robots?status=RUNNING"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("ROBOT_FILTER_INVALID"))

        mockMvc.perform(get("/api/v1/admin/robots?connectionStatus=CONNECTED"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("ROBOT_FILTER_INVALID"))
    }

    @Test
    fun `Robot을 alias 버전 capability 운영 상태로 수정한다`() {
        val originalAlias = "수정-원본-${UUID.randomUUID()}"
        val id = register(alias = originalAlias)["data"]["robotId"].asText()

        mockMvc.perform(
            patch("/api/v1/admin/robots/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("수정-완료", firmwareVersion = "1.2.3", sdkVersion = "2.0.0", operationStatus = "READY")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.alias").value("수정-완료"))
            .andExpect(jsonPath("$.data.firmwareVersion").value("1.2.3"))
            .andExpect(jsonPath("$.data.sdkVersion").value("2.0.0"))
            .andExpect(jsonPath("$.data.operationalStatus").value("READY"))
            .andExpect(jsonPath("$.data.capabilities[0].code").value("TURN"))
    }

    @Test
    fun `PATCH에서 생략한 필드는 유지하고 빈 capability 배열은 전체 삭제한다`() {
        val id = register(
            alias = "부분 수정 원본-${UUID.randomUUID()}",
            capabilities = "[{\"code\":\"MOVE\",\"status\":\"UNVERIFIED\"}]",
        )["data"]["robotId"].asText()

        mockMvc.perform(
            patch("/api/v1/admin/robots/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"alias":"부분 수정 완료"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.alias").value("부분 수정 완료"))
            .andExpect(jsonPath("$.data.firmwareVersion").value("1.0.0"))
            .andExpect(jsonPath("$.data.capabilities[0].code").value("MOVE"))

        mockMvc.perform(
            patch("/api/v1/admin/robots/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"capabilities":[]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.capabilities").isEmpty)
    }

    @Test
    fun `기존 capability를 같은 code로 수정해도 유일성 제약을 위반하지 않는다`() {
        val id = register(
            alias = "capability 수정-${UUID.randomUUID()}",
            capabilities = "[{\"code\":\"MOVE\",\"status\":\"UNVERIFIED\"}]",
        )["data"]["robotId"].asText()

        mockMvc.perform(
            patch("/api/v1/admin/robots/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"capabilities":[{"code":"MOVE","status":"VERIFIED"}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.capabilities[0].code").value("MOVE"))
            .andExpect(jsonPath("$.data.capabilities[0].status").value("VERIFIED"))
    }

    @Test
    fun `존재하지 않는 Robot과 빈 PATCH를 거부한다`() {
        mockMvc.perform(
            patch("/api/v1/admin/robots/${UUID.randomUUID()}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("없는-로봇")),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"))

        mockMvc.perform(
            patch("/api/v1/admin/robots/${UUID.randomUUID()}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isBadRequest)
    }

    private fun register(
        alias: String,
        agentId: UUID? = null,
        safetyProfileId: UUID? = null,
        capabilities: String = "[]",
        isExternal: Boolean = false,
    ): JsonNode {
        val result = mockMvc.perform(
            post("/api/v1/admin/robots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson(alias, agentId, safetyProfileId, capabilities, isExternal)),
        )
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString).also {
            if (result.response.status >= 400) {
                throw AssertionError("registration failed: ${result.response.contentAsString}")
            }
        }
    }

    private fun registrationJson(
        alias: String,
        agentId: UUID? = null,
        safetyProfileId: UUID? = null,
        capabilities: String = "[]",
        isExternal: Boolean = false,
    ): String = """
        {
          "alias":"$alias",
          "model":"GO2",
          "edition":"EDU",
          "firmwareVersion":"1.0.0",
          "sdkVersion":"2.0.0",
          "agentId":${agentId?.let { "\"$it\"" } ?: "null"},
          "capabilities":$capabilities,
          "safetyProfileId":${safetyProfileId?.let { "\"$it\"" } ?: "null"},
          "isExternal":$isExternal
        }
    """.trimIndent()

    private fun updateJson(
        alias: String,
        firmwareVersion: String = "1.0.0",
        sdkVersion: String = "2.0.0",
        operationStatus: String = "UNAVAILABLE",
    ): String = """
        {
          "alias":"$alias",
          "firmwareVersion":"$firmwareVersion",
          "sdkVersion":"$sdkVersion",
          "capabilities":[{"code":"TURN","status":"VERIFIED"}],
          "operationalStatus":"$operationStatus"
        }
    """.trimIndent()

}
