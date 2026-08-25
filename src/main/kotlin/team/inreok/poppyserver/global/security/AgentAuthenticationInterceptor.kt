package team.inreok.poppyserver.global.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Component
class AgentAuthenticationInterceptor(
    @Value("\${poppy.agent.token:}") private val configuredToken: String,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val provided = request.getHeader("X-Agent-Token")?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf()
        val expected = configuredToken.toByteArray(StandardCharsets.UTF_8)
        if (expected.isEmpty() || !MessageDigest.isEqual(provided, expected)) {
            throw ApplicationException(ErrorCode.AGENT_AUTH_INVALID)
        }
        return true
    }
}

@Component
class AgentAuthenticationWebConfig(
    private val agentAuthenticationInterceptor: AgentAuthenticationInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(agentAuthenticationInterceptor)
            .addPathPatterns("/api/v1/internal/**")
    }
}
