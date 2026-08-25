package team.inreok.poppyserver.infrastructure

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import javax.sql.DataSource
import kotlin.test.assertTrue

@SpringBootTest
class PostgresConnectionIntegrationTest : PostgresIntegrationTest() {

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

}
