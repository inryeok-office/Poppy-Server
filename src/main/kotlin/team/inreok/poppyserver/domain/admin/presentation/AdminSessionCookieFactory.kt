package team.inreok.poppyserver.domain.admin.presentation

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import team.inreok.poppyserver.global.security.AdminSessionCredentials

@Component
class AdminSessionCookieFactory(
    @Value("\${server.servlet.session.cookie.same-site:strict}") sameSite: String,
    @Value("\${server.servlet.session.cookie.secure:true}") private val secure: Boolean,
) {
    private val sameSite: String

    init {
        this.sameSite = when (sameSite.trim().lowercase()) {
            "strict" -> "Strict"
            "lax" -> "Lax"
            else -> throw IllegalStateException(
                "server.servlet.session.cookie.same-site(SESSION_COOKIE_SAME_SITE)는 Strict 또는 Lax만 허용한다. 입력값: $sameSite"
            )
        }
    }

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
