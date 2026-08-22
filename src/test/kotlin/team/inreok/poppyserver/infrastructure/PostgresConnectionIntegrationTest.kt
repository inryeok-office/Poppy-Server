package team.inreok.poppyserver.infrastructure

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
class PostgresConnectionIntegrationTest {

    @Autowired
    lateinit var dataSource: DataSource

    @Test
    fun `Postgres 컨테이너에 연결하고 Flyway 마이그레이션이 적용된다`() {
        dataSource.connection.use { connection ->
            connection.createStatement().executeQuery("SELECT 1").use { resultSet ->
                assertTrue(resultSet.next())
            }
            connection.metaData.getTables(null, null, "flyway_schema_history", null).use { tables ->
                assertTrue(tables.next())
            }
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
