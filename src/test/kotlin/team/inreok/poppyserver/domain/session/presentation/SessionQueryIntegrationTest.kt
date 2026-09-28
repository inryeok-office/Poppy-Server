package team.inreok.poppyserver.domain.session.presentation

import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(properties = ["poppy.session.inactivity-timeout=PT30M"])
@AutoConfigureMockMvc
class SessionQueryIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `활성 Session 조회는 nullable 상태와 inactivity deadline을 반환한다`() {
        val created = sessionService.createSession()
        val before = Instant.now()

        val result = mockMvc.perform(
            get("/api/v1/sessions/${created.sessionId}")
                .header("X-Session-Token", created.sessionToken),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sessionId").value(created.sessionId.toString()))
            .andExpect(jsonPath("$.data.mode").value(null))
            .andExpect(jsonPath("$.data.missionId").value(null))
            .andExpect(jsonPath("$.data.blockVersion").value(0))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn()

        val data = objectMapper.readTree(result.response.contentAsString).get("data")
        val lastActivityAt = Instant.parse(data.get("lastActivityAt").asString())
        val expiresAt = Instant.parse(data.get("expiresAt").asString())
        val persistedActivityAt = jdbcTemplate.queryForObject(
            "SELECT last_activity_at FROM sessions WHERE id = ?",
            java.sql.Timestamp::class.java,
            created.sessionId,
        )!!.toInstant()

        assertTrue(!lastActivityAt.isBefore(before))
        assertTrue(!persistedActivityAt.isBefore(before))
        assertEquals(Duration.ofMinutes(30), Duration.between(lastActivityAt, expiresAt))
    }

    @Test
    fun `persisted mode와 missionId를 조회 응답에 반영한다`() {
        val created = sessionService.createSession()
        val missionId = UUID.randomUUID()
        jdbcTemplate.update(
            "UPDATE sessions SET mode = ?, mission_id = ? WHERE id = ?",
            "MISSION",
            missionId,
            created.sessionId,
        )

        mockMvc.perform(
            get("/api/v1/sessions/${created.sessionId}")
                .header("X-Session-Token", created.sessionToken),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.mode").value("MISSION"))
            .andExpect(jsonPath("$.data.missionId").value(missionId.toString()))
    }

    @Test
    fun `Session 조회 인증 오류와 만료 오류를 기존 계약으로 반환한다`() {
        val created = sessionService.createSession()

        mockMvc.perform(get("/api/v1/sessions/${created.sessionId}"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))

        mockMvc.perform(
            get("/api/v1/sessions/${created.sessionId}")
                .header("X-Session-Token", "invalid-token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))

        mockMvc.perform(
            get("/api/v1/sessions/${UUID.randomUUID()}")
                .header("X-Session-Token", created.sessionToken),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"))

        jdbcTemplate.update(
            "UPDATE sessions SET expired_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now()),
            created.sessionId,
        )

        mockMvc.perform(
            get("/api/v1/sessions/${created.sessionId}")
                .header("X-Session-Token", created.sessionToken),
        )
            .andExpect(status().isGone)
            .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"))
    }
}
