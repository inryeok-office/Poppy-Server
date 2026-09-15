package team.inreok.poppyserver.global.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.ObjectProvider
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
    @Value("\${poppy.agent.token:}") private val bootstrapToken: String,
    private val agentPrincipalResolver: ObjectProvider<AgentPrincipalResolver>,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val provided = request.getHeader("X-Agent-Token") ?: ""
        if (request.requestURI.removePrefix(request.contextPath) == REGISTER_PATH) {
            if (!matches(provided, bootstrapToken)) {
                throw ApplicationException(ErrorCode.AGENT_AUTH_INVALID)
            }
            return true
        }

        val resolver = agentPrincipalResolver.getIfAvailable()
            ?: throw ApplicationException(ErrorCode.AGENT_AUTH_INVALID)
        val authenticatedAgentId = resolver.resolveAgentId(provided)
            ?: throw ApplicationException(ErrorCode.AGENT_AUTH_INVALID)
        if (authenticatedAgentId != pathAgentId(request)) {
            throw ApplicationException(ErrorCode.AGENT_PRINCIPAL_MISMATCH)
        }
        return true
    }

    private fun pathAgentId(request: HttpServletRequest): UUID {
        val path = request.requestURI.removePrefix(request.contextPath)
        val value = path.removePrefix(AGENT_PATH_PREFIX).substringBefore('/')
        return try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw ApplicationException(ErrorCode.AGENT_AUTH_INVALID)
        }
    }

    private fun matches(provided: String, expected: String): Boolean =
        expected.isNotEmpty() && MessageDigest.isEqual(
            provided.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8),
        )

    companion object {
        private const val REGISTER_PATH = "/api/v1/internal/agents/register"
        private const val AGENT_PATH_PREFIX = "/api/v1/internal/agents/"
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
