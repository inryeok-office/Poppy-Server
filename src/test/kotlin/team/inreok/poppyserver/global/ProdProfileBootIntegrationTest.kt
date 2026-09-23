package team.inreok.poppyserver.global

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import team.inreok.poppyserver.infrastructure.PostgresIntegrationContainer
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("prod")
@DirtiesContext
class ProdProfileBootIntegrationTest {

    @Autowired
    lateinit var context: ConfigurableApplicationContext

    @Test
    fun `prod 프로필은 환경변수 기반 datasource 설정만으로 컨텍스트가 기동된다`() {
        assertTrue(context.isActive)
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            val postgres = PostgresIntegrationContainer.postgres
            registry.add("DB_HOST") { postgres.host }
            registry.add("DB_PORT") { postgres.getMappedPort(5432) }
            registry.add("DB_NAME") { postgres.databaseName }
            registry.add("DB_USERNAME") { postgres.username }
            registry.add("DB_PASSWORD") { postgres.password }
            registry.add("spring.datasource.hikari.maximum-pool-size") { 2 }
            registry.add("spring.datasource.hikari.minimum-idle") { 1 }
            registry.add("poppy.execution.offline-recovery-enabled") { false }
        }
    }
}
