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
import kotlin.test.assertNotEquals
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AdminClientIpTrustedProxyIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `신뢰 프록시가 설정되면 XFF로 IP를 구분해 로그인 제한을 적용한다`() {
        repeat(IP_MAX_ATTEMPTS) { index -> login(forwardedFor = "203.0.113.10", username = "trusted-a-$index") }

        val exhausted = login(forwardedFor = "203.0.113.10", username = "trusted-a-final")
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exhausted.statusCode)

        val otherAddress = login(forwardedFor = "203.0.113.20", username = "trusted-b-final")
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, otherAddress.statusCode)
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
            registry.add("server.tomcat.remoteip.internal-proxies") { "127\\.0\\.0\\.1|0:0:0:0:0:0:0:1" }
            registry.add("poppy.admin.login.ip-max-attempts") { IP_MAX_ATTEMPTS }
            registry.add("poppy.admin.login.max-attempts") { 1000 }
        }
    }
}
