package team.inreok.poppyserver.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

object AdminSessionCredentials {
    const val COOKIE_NAME = "POPPY_ADMIN_SESSION"
    const val COOKIE_PATH = "/api/v1/admin"
    const val SESSION_ID_ATTRIBUTE = "poppy.admin.session-id"
}

@Component
class AdminAuthenticationInterceptor(
    private val adminSessionResolver: ObjectProvider<AdminSessionResolver>,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val token = request.cookies
            ?.firstOrNull { it.name == AdminSessionCredentials.COOKIE_NAME }
            ?.value
        if (token.isNullOrBlank()) {
            throw ApplicationException(ErrorCode.ADMIN_SESSION_INVALID)
        }
        val resolver = adminSessionResolver.getIfAvailable()
            ?: throw ApplicationException(ErrorCode.ADMIN_SESSION_INVALID)
        val adminSessionId = resolver.resolveAdminSessionId(token)
            ?: throw ApplicationException(ErrorCode.ADMIN_SESSION_INVALID)
        request.setAttribute(AdminSessionCredentials.SESSION_ID_ATTRIBUTE, adminSessionId)
        return true
    }
}

@Component
class AdminAuthenticationWebConfig(
    private val adminAuthenticationInterceptor: AdminAuthenticationInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminAuthenticationInterceptor)
            .addPathPatterns("/api/v1/admin/**")
            .excludePathPatterns("/api/v1/admin/auth/login")
    }
}
