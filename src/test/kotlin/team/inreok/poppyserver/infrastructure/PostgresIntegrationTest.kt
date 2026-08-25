package team.inreok.poppyserver.infrastructure

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgresIntegrationContainer {
    val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))

    init {
        postgres.start()
    }
}

abstract class PostgresIntegrationTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun registerPostgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", PostgresIntegrationContainer.postgres::getJdbcUrl)
            registry.add("spring.datasource.username", PostgresIntegrationContainer.postgres::getUsername)
            registry.add("spring.datasource.password", PostgresIntegrationContainer.postgres::getPassword)
        }
    }
}
