package team.inreok.poppyserver.domain.agent.presentation

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.poppyserver.domain.agent.application.AgentRepository
import team.inreok.poppyserver.domain.agent.application.AgentCredentialService
import team.inreok.poppyserver.domain.agent.model.Agent
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class AgentExecutionDeliveryIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var agentRepository: AgentRepository

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `binding된 Robot의 ASSIGNED Execution을 delivery envelope으로 조회한다`() {
        val agent = saveAgent()
        val execution = saveAssignedExecution()
        val robot = saveBoundRobot(agent.id, execution.id)

        mockMvc.perform(nextRequest(agent.id, robot.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.execution.executionId").value(execution.id.toString()))
            .andExpect(jsonPath("$.data.execution.robotId").value(robot.id.toString()))
            .andExpect(jsonPath("$.data.execution.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.data.execution.protocolVersion").value(1))
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `currentExecutionId가 없으면 no-work를 정상 응답으로 반환한다`() {
        val agent = saveAgent()
        val robot = saveBoundRobot(agent.id, null)

        mockMvc.perform(nextRequest(agent.id, robot.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.execution").isEmpty)
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `다른 Agent에 binding된 Robot 조회를 거부한다`() {
        val boundAgent = saveAgent()
        val requestingAgent = saveAgent()
        val robot = saveBoundRobot(boundAgent.id, null)

        mockMvc.perform(nextRequest(requestingAgent.id, robot.id))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("AGENT_ROBOT_BINDING_MISMATCH"))
    }

    @Test
    fun `존재하지 않는 Robot과 Agent를 기존 오류 계약으로 처리한다`() {
        val agent = saveAgent()

        mockMvc.perform(nextRequest(agent.id, UUID.randomUUID()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"))

        mockMvc.perform(nextRequest(UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))
    }

    @Test
    fun `끊어진 currentExecutionId와 ASSIGNED 외 상태를 invariant 오류로 처리한다`() {
        val agent = saveAgent()
        val missingExecutionRobot = saveBoundRobot(agent.id, UUID.randomUUID())

        mockMvc.perform(nextRequest(agent.id, missingExecutionRobot.id))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_DELIVERY_INVARIANT_VIOLATED"))

        val queuedExecution = saveQueuedExecution()
        val queuedExecutionRobot = saveBoundRobot(agent.id, queuedExecution.id)

        mockMvc.perform(nextRequest(agent.id, queuedExecutionRobot.id))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_DELIVERY_INVARIANT_VIOLATED"))
    }

    @Test
    fun `반복 polling은 Execution과 Robot 상태를 변경하지 않는다`() {
        val agent = saveAgent()
        val execution = saveAssignedExecution()
        val robot = saveBoundRobot(agent.id, execution.id)

        val firstResponse = mockMvc.perform(nextRequest(agent.id, robot.id))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val secondResponse = mockMvc.perform(nextRequest(agent.id, robot.id))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        assertEquals(objectMapper.readTree(firstResponse), objectMapper.readTree(secondResponse))
        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(execution.id)?.status)
        assertEquals(execution.id, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `잘못된 Agent token을 거부한다`() {
        mockMvc.perform(
            get("/api/v1/internal/agents/${UUID.randomUUID()}/executions/next")
                .param("robotId", UUID.randomUUID().toString())
                .header("X-Agent-Token", "wrong-token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))
    }

    private fun saveAgent(): Agent = inTransaction {
        val agent = Agent.register(
                name = "delivery-agent-${UUID.randomUUID()}",
                agentVersion = "1.0.0",
                sdkVersion = "2.0.0",
                platform = "linux-arm64",
                registeredAt = Instant.parse("2026-09-14T00:00:00Z"),
            ).apply { rotateCredential(AgentCredentialService.digest(id.toString())) }
        agentRepository.save(agent)
    }

    private fun saveBoundRobot(agentId: UUID, currentExecutionId: UUID?): Robot = inTransaction {
        robotRepository.save(
            Robot.register(
                alias = "delivery-robot-${UUID.randomUUID()}",
                model = "GO2",
                agentId = agentId,
            ).apply {
                applyHeartbeat(
                    at = Instant.parse("2026-09-14T00:00:00Z"),
                    connectionStatus = RobotConnectionStatus.ONLINE,
                    operationStatus = RobotOperationStatus.READY,
                    batteryPercent = null,
                    currentExecutionId = currentExecutionId,
                )
            },
        )
    }

    private fun saveAssignedExecution(): Execution = inTransaction {
        executionRepository.save(Execution.create().apply { assign() })
    }

    private fun saveQueuedExecution(): Execution = inTransaction {
        executionRepository.save(Execution.create())
    }

    private fun <T> inTransaction(action: () -> T): T = requireNotNull(
        TransactionTemplate(transactionManager).execute { action() },
    )

    private fun nextRequest(agentId: UUID, robotId: UUID) =
        get("/api/v1/internal/agents/$agentId/executions/next")
            .param("robotId", robotId.toString())
            .header("X-Agent-Token", agentId.toString())

    companion object {
        private const val TEST_TOKEN = "test-agent-token"

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("poppy.agent.token") { TEST_TOKEN }
        }
    }
}
