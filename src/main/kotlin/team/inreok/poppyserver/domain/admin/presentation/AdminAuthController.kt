package team.inreok.poppyserver.domain.admin.presentation

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.admin.application.AdminAuthenticationService
import team.inreok.poppyserver.global.response.ApiResponse
import team.inreok.poppyserver.global.security.AdminSessionCredentials

@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/admin/auth")
class AdminAuthController(
    private val adminAuthenticationService: AdminAuthenticationService,
    private val adminSessionCookieFactory: AdminSessionCookieFactory,
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: AdminLoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<AdminLoginResponse>> {
        val opened = adminAuthenticationService.login(
            username = requireNotNull(request.username),
            password = requireNotNull(request.password),
            clientAddress = httpRequest.remoteAddr,
        )
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, adminSessionCookieFactory.issue(opened.token, opened.ttl).toString())
            .body(ApiResponse.success(AdminLoginResponse(authenticated = true)))
    }

    @PostMapping("/logout")
    fun logout(
        @RequestAttribute(AdminSessionCredentials.SESSION_ID_ATTRIBUTE) adminSessionId: UUID,
    ): ResponseEntity<Void> {
        adminAuthenticationService.logout(adminSessionId)
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, adminSessionCookieFactory.expire().toString())
            .build()
    }
}

data class AdminLoginRequest(
    @field:NotBlank @field:Size(max = 64) val username: String?,
    @field:NotBlank @field:Size(max = 128) val password: String?,
) {
    override fun toString(): String = "AdminLoginRequest()"
}

data class AdminLoginResponse(
    val authenticated: Boolean,
)
