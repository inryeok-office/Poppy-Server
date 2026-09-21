package team.inreok.poppyserver.support

import jakarta.servlet.http.Cookie
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

object AdminSessionTestSupport {
    const val COOKIE_NAME = "POPPY_ADMIN_SESSION"
    const val TOKEN = "test-support-admin-session-token"

    fun insertValidSession(jdbcTemplate: JdbcTemplate) {
        val digest = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(TOKEN.toByteArray()))
        jdbcTemplate.update("DELETE FROM admin_sessions WHERE session_token_digest = ?", digest)
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO admin_sessions (id, session_token_digest, created_at, expires_at) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            digest,
            Timestamp.from(now),
            Timestamp.from(now.plusSeconds(3600)),
        )
    }
}

@TestConfiguration
class AdminSessionCookieTestConfiguration {
    @Bean
    fun adminSessionCookieCustomizer(): MockMvcBuilderCustomizer = MockMvcBuilderCustomizer { builder ->
        builder.defaultRequest(get("/").cookie(Cookie(AdminSessionTestSupport.COOKIE_NAME, AdminSessionTestSupport.TOKEN)))
    }
}
