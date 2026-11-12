package team.inreok.poppyserver.domain.arena.presentation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest

/** 관리자 세션 쿠키가 없으면 공간 설정을 바꾸거나 이력을 볼 수 없다. 조회(공개)는 된다. */
@SpringBootTest
@AutoConfigureMockMvc
class ArenaSettingsAdminBoundaryTest : PostgresIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `관리자 세션 없이 PUT 과 이력 조회는 401 이고 공개 조회는 200 이다`() {
        mockMvc.perform(
            put("/api/v1/admin/arena-settings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"presetCode":"3x3-부스"}"""),
        ).andExpect(status().isUnauthorized).andExpect(jsonPath("$.error.code").value("ADMIN_SESSION_INVALID"))
        mockMvc.perform(get("/api/v1/admin/arena-settings/history")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/arena-settings")).andExpect(status().isOk)
    }
}
