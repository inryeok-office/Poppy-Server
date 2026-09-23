package team.inreok.poppyserver.global.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `actuator health는 공개되고 나머지 actuator는 거부된다`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/actuator/env"))
            .andExpect(status().isForbidden)
    }
}
