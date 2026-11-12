package team.inreok.poppyserver.domain.arena.presentation

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.domain.block.application.BlockDefinitionCatalog
import team.inreok.poppyserver.domain.block.application.BlockProgramLimitValidator
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import team.inreok.poppyserver.support.AdminSessionCookieTestConfiguration
import team.inreok.poppyserver.support.AdminSessionTestSupport
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminSessionCookieTestConfiguration::class)
class ArenaSettingsIntegrationTest : PostgresIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var validator: BlockProgramLimitValidator

    @Autowired lateinit var catalog: BlockDefinitionCatalog

    @BeforeEach
    fun setUp() {
        AdminSessionTestSupport.insertValidSession(jdbcTemplate)
        clean()
    }

    @AfterEach
    fun clean() {
        jdbcTemplate.update("DELETE FROM experience_arena_setting_changes")
        jdbcTemplate.update("DELETE FROM experience_arena_settings")
    }

    private fun putJson(body: String) =
        mockMvc.perform(put("/api/v1/admin/arena-settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun move(meters: Double) = BlockProgram(
        schemaVersion = 1,
        blocks = listOf(BlockInstance("m", BlockType.MOVE_FORWARD, BlockParameters.DistanceMeters(meters))),
    )

    @Test
    fun `저장된 설정이 없으면 6x6 박람회 기본값이고 updatedAt 은 null 이다`() {
        mockMvc.perform(get("/api/v1/arena-settings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.presetCode").value("6x6-박람회"))
            .andExpect(jsonPath("$.data.widthM").value(6.0))
            .andExpect(jsonPath("$.data.startXM").value(5.6))
            .andExpect(jsonPath("$.data.limits.maxMovePerCommandM").value(3.0))
            .andExpect(jsonPath("$.data.limits.defaultMoveDistanceM").value(1.0))
            .andExpect(jsonPath("$.data.updatedAt").doesNotExist())
        assertTrue(validator.validate(move(3.0)).isValid)
    }

    @Test
    fun `프리셋 목록은 2종이다`() {
        mockMvc.perform(get("/api/v1/arena-settings/presets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[1].presetCode").value("3x3-부스"))
            .andExpect(jsonPath("$.data[1].limits.maxMovePerCommandM").value(1.2))
    }

    @Test
    fun `관리자가 3x3 프리셋으로 바꾸면 조회 블록 검증 카탈로그가 함께 바뀌고 이력이 남는다`() {
        putJson("""{"presetCode":"3x3-부스"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.presetCode").value("3x3-부스"))
            .andExpect(jsonPath("$.data.limits.usableWidthM").value(2.0))
            .andExpect(jsonPath("$.data.limits.maxMovePerCommandM").value(1.2))
            .andExpect(jsonPath("$.data.limits.defaultMoveDistanceM").value(0.5))

        mockMvc.perform(get("/api/v1/arena-settings"))
            .andExpect(jsonPath("$.data.widthM").value(3.0))
            .andExpect(jsonPath("$.data.marginM").value(0.5))
            .andExpect(jsonPath("$.data.startXM").value(2.5))
            .andExpect(jsonPath("$.data.startYM").value(0.5))

        assertTrue(validator.validate(move(1.2)).isValid)
        assertFalse(validator.validate(move(1.3)).isValid)
        assertEquals(1.2, catalog.list().first { it.type == BlockType.MOVE_FORWARD }.parameters.single().max)

        mockMvc.perform(get("/api/v1/admin/arena-settings/history"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].previous.presetCode").value("6x6-박람회"))
            .andExpect(jsonPath("$.data[0].next.presetCode").value("3x3-부스"))
            .andExpect(jsonPath("$.data[0].changedBy").value(org.hamcrest.Matchers.startsWith("admin-session:")))
    }

    @Test
    fun `개별 값으로 바꿀 수 있고 다시 6x6 으로 되돌리면 기존 동작이다`() {
        putJson("""{"widthM":4.0,"depthM":3.0,"marginM":0.5,"startXM":3.5,"startYM":0.5,"startHeading":"+Y"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.presetCode").doesNotExist())
            .andExpect(jsonPath("$.data.limits.maxMovePerCommandM").value(1.2))
        putJson("""{"presetCode":"6x6-박람회"}""").andExpect(status().isOk)
        assertTrue(validator.validate(move(3.0)).isValid)
        mockMvc.perform(get("/api/v1/admin/arena-settings/history")).andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    fun `잘못된 값은 400 이고 설정과 이력이 바뀌지 않는다`() {
        putJson("""{"widthM":9.0,"depthM":3.0,"marginM":0.5,"startXM":3.5,"startYM":0.5,"startHeading":"+Y"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("ARENA_SETTINGS_INVALID"))
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("widthM"))
        putJson("""{"widthM":3.0,"depthM":3.0,"marginM":0.3,"startXM":2.5,"startYM":0.5,"startHeading":"+Y"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("marginM"))
        putJson("""{"widthM":3.0,"depthM":3.0,"marginM":0.5,"startXM":0.2,"startYM":0.5,"startHeading":"+Y"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("startXM"))
        putJson("""{"presetCode":"없는-프리셋"}""").andExpect(status().isBadRequest)
        putJson("""{"presetCode":"3x3-부스","widthM":3.0}""").andExpect(status().isBadRequest)
        putJson("""{"widthM":3.0}""").andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/admin/arena-settings/history")).andExpect(jsonPath("$.data.length()").value(0))
        mockMvc.perform(get("/api/v1/arena-settings")).andExpect(jsonPath("$.data.presetCode").value("6x6-박람회"))
    }
}
