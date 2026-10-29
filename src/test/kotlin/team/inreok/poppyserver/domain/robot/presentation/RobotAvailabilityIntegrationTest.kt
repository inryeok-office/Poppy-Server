package team.inreok.poppyserver.domain.robot.presentation

import java.time.Instant
import java.util.UUID
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RobotAvailabilityIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Test
    fun `인증 없이 alias와 가용 상태만 반환한다`() {
        val alias = "availability-${UUID.randomUUID()}"
        val robot = Robot.register(alias = alias, model = "GO2", agentId = UUID.randomUUID()).apply {
            applyHeartbeat(
                at = Instant.now(),
                connectionStatus = RobotConnectionStatus.ONLINE,
                operationStatus = RobotOperationStatus.READY,
                batteryPercent = 70,
                currentExecutionId = null,
            )
        }
        robotRepository.save(robot)

        mockMvc.perform(get("/api/v1/robots/availability"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.availableCount").isNumber)
            .andExpect(jsonPath("$.data.total").isNumber)
            .andExpect(jsonPath("$.data.robots[?(@.alias == '$alias')].status").value("READY"))
            .andExpect(jsonPath("$.data.robots[0].robotId").doesNotExist())
            .andExpect(jsonPath("$.data.robots[0].agentId").doesNotExist())
            .andExpect(jsonPath("$.data.robots[0].capabilities").doesNotExist())
            .andExpect(jsonPath("$.data.robots[0].currentExecutionId").doesNotExist())
            .andExpect(jsonPath("$.data.robots[0].batteryPercent").doesNotExist())
            .andExpect(jsonPath("$.data.robots[*].alias", hasItem(alias)))
    }

    @Test
    fun `비활성화된 Robot은 응답에 포함하지 않는다`() {
        val alias = "retired-${UUID.randomUUID()}"
        robotRepository.save(Robot.register(alias = alias, model = "GO2").apply { deactivate() })

        mockMvc.perform(get("/api/v1/robots/availability"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.robots[*].alias", not(hasItem(alias))))
    }
}
