package team.inreok.poppyserver.domain.session.presentation

import java.util.UUID
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.domain.execution.application.ExecutionRequestService
import team.inreok.poppyserver.domain.session.application.SessionCreationResult
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.domain.session.application.SimulationPassRepository
import team.inreok.poppyserver.domain.session.application.SimulationPassService
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import team.inreok.poppyserver.support.validBlockProgram
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(properties = ["poppy.execution.queue-dispatch-enabled=false"])
@AutoConfigureMockMvc
class SessionExperienceIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var simulationPassService: SimulationPassService

    @Autowired
    lateinit var simulationPassRepository: SimulationPassRepository

    @Autowired
    lateinit var executionRequestService: ExecutionRequestService

    @Test
    fun `MISSION 모드와 missionId를 저장하고 조회 응답에 반영한다`() {
        val session = sessionService.createSession()

        changeExperience(session, """{"mode": "MISSION", "missionId": "$DELIVERY_MISSION_ID"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sessionId").value(session.sessionId.toString()))
            .andExpect(jsonPath("$.data.mode").value("MISSION"))
            .andExpect(jsonPath("$.data.missionId").value(DELIVERY_MISSION_ID))

        mockMvc.perform(
            get("/api/v1/sessions/${session.sessionId}")
                .header("X-Session-Token", session.sessionToken),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.mode").value("MISSION"))
            .andExpect(jsonPath("$.data.missionId").value(DELIVERY_MISSION_ID))
    }

    @Test
    fun `FREE 모드로 바꾸면 missionId가 비워진다`() {
        val session = sessionService.createSession()
        changeExperience(session, """{"mode": "MISSION", "missionId": "$DELIVERY_MISSION_ID"}""")
            .andExpect(status().isOk)

        changeExperience(session, """{"mode": "FREE"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.mode").value("FREE"))
            .andExpect(jsonPath("$.data.missionId").value(null))
    }

    @Test
    fun `mode와 missionId 조합이 맞지 않으면 COMMON_400을 반환한다`() {
        val session = sessionService.createSession()

        changeExperience(session, """{"mode": "MISSION"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("missionId"))
        changeExperience(session, """{"mode": "FREE", "missionId": "$DELIVERY_MISSION_ID"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
        changeExperience(session, """{}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
    }

    @Test
    fun `catalog에 없는 missionId는 MISSION_NOT_FOUND를 반환한다`() {
        val session = sessionService.createSession()

        changeExperience(session, """{"mode": "MISSION", "missionId": "${UUID.randomUUID()}"}""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("MISSION_NOT_FOUND"))
    }

    @Test
    fun `Session token이 없거나 틀리면 SESSION_TOKEN_INVALID를 반환한다`() {
        val session = sessionService.createSession()

        mockMvc.perform(
            patch("/api/v1/sessions/${session.sessionId}")
                .header("X-Session-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode": "FREE"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))
    }

    @Test
    fun `진행 중인 Execution이 있으면 409 EXECUTION_SESSION_ACTIVE를 반환한다`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, validBlockProgram())
        simulationPassService.recordSimulationPass(session.sessionId, 1)
        executionRequestService.requestExecution(session.sessionId, 1)

        changeExperience(session, """{"mode": "FREE"}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_SESSION_ACTIVE"))
    }

    @Test
    fun `모드가 바뀌면 기존 simulation pass가 무효화된다`() {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, validBlockProgram())
        simulationPassService.recordSimulationPass(session.sessionId, 1)
        assertNotNull(simulationPassRepository.findById(session.sessionId, 1))

        changeExperience(session, """{"mode": "MISSION", "missionId": "$DELIVERY_MISSION_ID"}""")
            .andExpect(status().isOk)

        assertNull(simulationPassRepository.findById(session.sessionId, 1))
    }

    @Test
    fun `같은 모드로 다시 요청하면 simulation pass를 유지한다`() {
        val session = sessionService.createSession()
        changeExperience(session, """{"mode": "FREE"}""").andExpect(status().isOk)
        sessionService.appendBlockRevision(session.sessionId, validBlockProgram())
        simulationPassService.recordSimulationPass(session.sessionId, 1)

        changeExperience(session, """{"mode": "FREE"}""").andExpect(status().isOk)

        assertNotNull(simulationPassRepository.findById(session.sessionId, 1))
    }

    @Test
    fun `MISSION 세션은 allowedBlocks 밖 블록 저장을 BLOCK_NOT_ALLOWED_IN_MISSION으로 거부한다`() {
        val session = sessionService.createSession()
        changeExperience(session, """{"mode": "MISSION", "missionId": "$DELIVERY_MISSION_ID"}""")
            .andExpect(status().isOk)

        appendRevision(session, programWithTurnLeft())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BLOCK_PROGRAM_INVALID"))
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("blocks[1]"))
            .andExpect(
                jsonPath("$.error.fieldErrors[0].reason")
                    .value(startsWith("BLOCK_NOT_ALLOWED_IN_MISSION")),
            )
    }

    @Test
    fun `MISSION 세션은 allowedBlocks와 START END만 쓴 블록을 저장한다`() {
        val session = sessionService.createSession()
        changeExperience(session, """{"mode": "MISSION", "missionId": "$DELIVERY_MISSION_ID"}""")
            .andExpect(status().isOk)

        appendRevision(session, validBlockProgram())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.blockVersion").value(1))
    }

    @Test
    fun `FREE 세션은 allowedBlocks 제한 없이 블록을 저장한다`() {
        val session = sessionService.createSession()
        changeExperience(session, """{"mode": "FREE"}""").andExpect(status().isOk)

        appendRevision(session, programWithTurnLeft())
            .andExpect(status().isCreated)
    }

    @Test
    fun `FREE에서 저장한 블록을 MISSION으로 바꾼 뒤 시뮬레이션 통과시키면 거부한다`() {
        val session = sessionService.createSession()
        appendRevision(session, programWithTurnLeft()).andExpect(status().isCreated)
        changeExperience(session, """{"mode": "MISSION", "missionId": "$DELIVERY_MISSION_ID"}""")
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/simulation-passes")
                .header("X-Session-Token", session.sessionToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"blockVersion": 1}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BLOCK_PROGRAM_INVALID"))
            .andExpect(
                jsonPath("$.error.fieldErrors[0].reason")
                    .value(startsWith("BLOCK_NOT_ALLOWED_IN_MISSION")),
            )
        assertNull(simulationPassRepository.findById(session.sessionId, 1))
    }

    private fun changeExperience(session: SessionCreationResult, body: String): ResultActions = mockMvc.perform(
        patch("/api/v1/sessions/${session.sessionId}")
            .header("X-Session-Token", session.sessionToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    private fun appendRevision(session: SessionCreationResult, document: String): ResultActions = mockMvc.perform(
        post("/api/v1/sessions/${session.sessionId}/block-revisions")
            .header("X-Session-Token", session.sessionToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"document": $document}"""),
    )

    private fun programWithTurnLeft(): String =
        """{"blocks": [{"id": "start", "type": "START", "parameters": {}}, {"id": "turn", "type": "TURN_LEFT", "parameters": {"angleDegrees": 90}}, {"id": "end", "type": "END", "parameters": {}}], "schemaVersion": 1}"""

    companion object {
        private const val DELIVERY_MISSION_ID = "f3f450e4-8faf-4d2e-b2aa-6f76ac115412"
    }
}
