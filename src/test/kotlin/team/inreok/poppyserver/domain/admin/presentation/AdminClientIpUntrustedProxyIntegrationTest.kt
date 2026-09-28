package team.inreok.poppyserver.domain.admin.presentation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AdminClientIpUntrustedProxyIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `신뢰 프록시가 비어 있으면 XFF를 무시하고 실제 연결 IP로만 제한한다`() {
        repeat(IP_MAX_ATTEMPTS) { index -> login(forwardedFor = "203.0.113.10", username = "untrusted-a-$index") }

        val exhausted = login(forwardedFor = "203.0.113.10", username = "untrusted-a-final")
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exhausted.statusCode)

        val spoofed = login(forwardedFor = "203.0.113.20", username = "untrusted-b-final")
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, spoofed.statusCode)
    }

    private fun login(forwardedFor: String, username: String) = restTemplate.postForEntity(
        "/api/v1/admin/auth/login",
        HttpEntity(
            """{"username":"$username","password":"wrong-password"}""",
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-Forwarded-For", forwardedFor)
            },
        ),
        String::class.java,
    )

    companion object {
        private const val IP_MAX_ATTEMPTS = 3

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.hikari.maximum-pool-size") { 2 }
            registry.add("spring.datasource.hikari.minimum-idle") { 1 }
            registry.add("server.tomcat.remoteip.internal-proxies") { "" }
            registry.add("poppy.admin.login.ip-max-attempts") { IP_MAX_ATTEMPTS }
            registry.add("poppy.admin.login.max-attempts") { 1000 }
        }
    }
}
