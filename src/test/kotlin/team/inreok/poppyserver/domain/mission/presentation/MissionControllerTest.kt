package team.inreok.poppyserver.domain.mission.presentation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.global.error.ErrorCode

@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration",
    ],
)
@AutoConfigureMockMvc
class MissionControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET missions는 토큰 없이 8개 미션을 원본 순서대로 반환한다`() {
        mockMvc.perform(
            get("/api/v1/missions").accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.missions.length()").value(8))
            .andExpect(jsonPath("$.data.missions[0].title").value("로봇 택배 배달"))
            .andExpect(jsonPath("$.data.missions[0].difficulty").value("EASY"))
            .andExpect(jsonPath("$.data.missions[0].missionId").value("f3f450e4-8faf-4d2e-b2aa-6f76ac115412"))
            .andExpect(jsonPath("$.data.missions[7].title").value("최소 블록 챌린지"))
            .andExpect(jsonPath("$.data.missions[7].difficulty").value("HARD"))
            .andExpect(jsonPath("$.error").value(null))
    }

    @Test
    fun `GET missions detail은 상세 필드와 null 필드를 함께 반환한다`() {
        mockMvc.perform(
            get("/api/v1/missions/f3f450e4-8faf-4d2e-b2aa-6f76ac115412").accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.missionId").value("f3f450e4-8faf-4d2e-b2aa-6f76ac115412"))
            .andExpect(jsonPath("$.data.title").value("로봇 택배 배달"))
            .andExpect(jsonPath("$.data.difficulty").value("EASY"))
            .andExpect(jsonPath("$.data.completionCondition.description").isNotEmpty)
            .andExpect(jsonPath("$.data.allowedBlocks.length()").value(2))
            .andExpect(jsonPath("$.data.allowedBlocks[0]").value("MOVE_FORWARD"))
            .andExpect(jsonPath("$.data.allowedBlocks[1]").value("STOP"))
            .andExpect(jsonPath("$.data.timeLimitSeconds").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$..estimatedSeconds").doesNotExist())
            .andExpect(jsonPath("$.error").value(null))
    }

    @Test
    fun `존재하지 않는 missionId는 404 MISSION_NOT_FOUND를 반환한다`() {
        mockMvc.perform(
            get("/api/v1/missions/00000000-0000-0000-0000-000000000000").accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(null))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.MISSION_NOT_FOUND.code))
    }
}
