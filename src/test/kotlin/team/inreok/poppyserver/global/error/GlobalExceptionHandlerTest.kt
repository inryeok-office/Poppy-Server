package team.inreok.poppyserver.global.error

import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration",
    ],
)
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `ApplicationException은 공통 에러 포맷으로 응답한다`() {
        mockMvc.perform(get("/test/application-exception"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.code))
    }

    @Test
    fun `Validation 오류는 필드 오류를 포함해 응답한다`() {
        mockMvc.perform(
            post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":""}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"))
    }

    @Test
    fun `처리되지 않은 예외는 500 공통 포맷으로 응답한다`() {
        mockMvc.perform(get("/test/unhandled"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERNAL_SERVER_ERROR.code))
    }
}

@RestController
@RequestMapping("/test")
class GlobalExceptionHandlerTestController {

    @GetMapping("/application-exception")
    fun applicationException(): Nothing = throw ApplicationException(ErrorCode.INVALID_INPUT)

    @PostMapping("/validate")
    fun validate(
        @Validated @RequestBody request: ValidationRequest,
    ): String = request.name

    @GetMapping("/unhandled")
    fun unhandled(): Nothing = throw IllegalStateException("boom")
}

data class ValidationRequest(
    @field:NotBlank
    val name: String,
)
