package team.inreok.poppyserver.domain.admin.presentation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest

@SpringBootTest
@AutoConfigureMockMvc
class AdminCredentialNotConfiguredIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `credential이 설정되지 않으면 어떤 입력도 401 ADMIN_CREDENTIAL_INVALID이다`() {
        listOf(
            """{"username":"admin","password":"anything"}""",
            """{"username":"change-me","password":"change-me"}""",
        ).forEach { body ->
            mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value(ErrorCode.ADMIN_CREDENTIAL_INVALID.code))
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun registerEmptyAdminProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.hikari.maximum-pool-size") { 2 }
            registry.add("spring.datasource.hikari.minimum-idle") { 1 }
            registry.add("poppy.admin.username") { "" }
            registry.add("poppy.admin.password-hash") { "" }
        }
    }
}
