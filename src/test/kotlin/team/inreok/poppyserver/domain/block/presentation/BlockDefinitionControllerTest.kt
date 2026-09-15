package team.inreok.poppyserver.domain.block.presentation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration",
    ],
)
@AutoConfigureMockMvc
class BlockDefinitionControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET blocks returns the Block Program v1 catalog`() {
        mockMvc.perform(
            get("/api/v1/blocks").accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.schemaVersion").value(1))
            .andExpect(jsonPath("$.data.blocks.length()").value(12))
            .andExpect(jsonPath("$.data.blocks[0].type").value("START"))
            .andExpect(jsonPath("$.data.blocks[1].parameters[0].name").value("durationSeconds"))
            .andExpect(jsonPath("$.data.blocks[1].parameters[0].type").value("NUMBER"))
            .andExpect(jsonPath("$.data.blocks[11].type").value("PRESET"))
            .andExpect(jsonPath("$.data.blocks[11].available").value(false))
            .andExpect(jsonPath("$.data.blocks[11].parameters[0].options.length()").value(0))
            .andExpect(jsonPath("$.error").value(null))
    }
}
