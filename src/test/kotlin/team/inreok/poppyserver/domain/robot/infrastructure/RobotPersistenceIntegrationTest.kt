package team.inreok.poppyserver.domain.robot.infrastructure

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.Robot
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Testcontainers
@SpringBootTest
class RobotPersistenceIntegrationTest {

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Test
    @Transactional
    fun `Agent 중복은 flush 시점에 persistence 예외로 검증된다`() {
        val agentId = UUID.randomUUID()
        robotRepository.save(Robot.register(alias = "첫 번째", model = "GO2", agentId = agentId))

        assertFailsWith<DataIntegrityViolationException> {
            robotRepository.save(Robot.register(alias = "두 번째", model = "GO2", agentId = agentId))
        }
    }

    @Test
    @Transactional
    fun `active 상태를 persistence에 저장하고 복원한다`() {
        val robot = Robot.register(alias = "비활성 로봇", model = "GO2")
        robot.deactivate()

        robotRepository.save(robot)

        assertEquals(false, robotRepository.findById(robot.id)?.active)
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
