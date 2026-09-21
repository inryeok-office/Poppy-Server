package team.inreok.poppyserver.domain.admin.presentation

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import team.inreok.poppyserver.global.security.AdminSessionCredentials

@Component
class AdminSessionCookieFactory(
    @Value("\${poppy.admin.cookie.same-site:Strict}") private val sameSite: String,
    @Value("\${poppy.admin.cookie.secure:true}") private val secure: Boolean,
) {
    fun issue(token: String, ttl: Duration): ResponseCookie = build(token, ttl)

    fun expire(): ResponseCookie = build("", Duration.ZERO)

    private fun build(value: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(AdminSessionCredentials.COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .path(AdminSessionCredentials.COOKIE_PATH)
            .maxAge(maxAge)
            .sameSite(sameSite)
            .build()
}
