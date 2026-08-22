package team.inreok.poppyserver.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun poppyOpenApi(): OpenAPI = OpenAPI().info(
        Info().title("POPPY API").version("v1"),
    )
}
