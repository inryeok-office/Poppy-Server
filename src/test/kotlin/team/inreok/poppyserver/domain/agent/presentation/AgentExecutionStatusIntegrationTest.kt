package team.inreok.poppyserver.domain.agent.presentation

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
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
class AgentExecutionStatusIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var agentRepository: AgentRepository

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `ASSIGNED Execution을 RUNNING으로 전환한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.ASSIGNED)
        val robot = saveRobot(agent.id, execution.id)

        report(agent.id, execution.id, robot.id, "RUNNING")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.executionId").value(execution.id.toString()))
            .andExpect(jsonPath("$.data.robotId").value(robot.id.toString()))
            .andExpect(jsonPath("$.data.status").value("RUNNING"))

        assertEquals(ExecutionStatus.RUNNING, executionRepository.findById(execution.id)?.status)
        assertEquals(execution.id, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `RUNNING Execution을 COMPLETED로 전환하고 Robot 점유를 해제한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val robot = saveRobot(agent.id, execution.id)

        report(agent.id, execution.id, robot.id, "COMPLETED")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))

        assertEquals(ExecutionStatus.COMPLETED, executionRepository.findById(execution.id)?.status)
        assertEquals(null, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `RUNNING Execution을 FAILED로 전환하고 Robot 점유를 해제한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val robot = saveRobot(agent.id, execution.id)

        report(agent.id, execution.id, robot.id, "FAILED")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("FAILED"))

        assertEquals(ExecutionStatus.FAILED, executionRepository.findById(execution.id)?.status)
        assertEquals(null, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `Agent가 CANCELLED terminal status를 보고하고 Robot 점유를 해제한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val robot = saveRobot(agent.id, execution.id)

        report(agent.id, execution.id, robot.id, "CANCELLED")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))

        assertEquals(ExecutionStatus.CANCELLED, executionRepository.findById(execution.id)?.status)
        assertEquals(null, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `Agent가 자신의 Execution 상태를 조회한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.CANCELLED)
        val robot = saveRobot(agent.id, null)
        inTransaction {
            execution.bindRobot(robot.id)
            executionRepository.save(execution)
        }

        mockMvc.perform(
            get("/api/v1/internal/agents/${agent.id}/executions/${execution.id}/status")
                .param("robotId", robot.id.toString())
                .header("X-Agent-Token", agent.id.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.executionId").value(execution.id.toString()))
            .andExpect(jsonPath("$.data.robotId").value(robot.id.toString()))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
    }

    @Test
    fun `RUNNING 동일 상태 재보고를 성공 처리한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val robot = saveRobot(agent.id, execution.id)

        val first = report(agent.id, execution.id, robot.id, "RUNNING")
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val second = report(agent.id, execution.id, robot.id, "RUNNING")
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        assertEquals(objectMapper.readTree(first), objectMapper.readTree(second))
        assertEquals(ExecutionStatus.RUNNING, executionRepository.findById(execution.id)?.status)
        assertEquals(execution.id, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `COMPLETED 동일 상태 재보고를 점유 해제 후에도 성공 처리한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val robot = saveRobot(agent.id, execution.id)

        report(agent.id, execution.id, robot.id, "COMPLETED")
            .andExpect(status().isOk)
        report(agent.id, execution.id, robot.id, "COMPLETED")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))

        assertEquals(null, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `FAILED 동일 상태 재보고를 점유 해제 후에도 성공 처리한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val robot = saveRobot(agent.id, execution.id)

        report(agent.id, execution.id, robot.id, "FAILED")
            .andExpect(status().isOk)
        report(agent.id, execution.id, robot.id, "FAILED")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("FAILED"))

        assertEquals(null, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `다른 Agent의 Robot Execution 상태 변경을 거부한다`() {
        val owner = saveAgent()
        val requester = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val robot = saveRobot(owner.id, execution.id)

        report(requester.id, execution.id, robot.id, "COMPLETED")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("AGENT_ROBOT_BINDING_MISMATCH"))
    }

    @Test
    fun `다른 Robot ID로 Execution 상태 변경을 거부한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val assignedRobot = saveRobot(agent.id, execution.id)
        val otherRobot = saveRobot(agent.id, null)

        report(agent.id, execution.id, otherRobot.id, "COMPLETED")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_ROBOT_MISMATCH"))
        assertEquals(execution.id, robotRepository.findById(assignedRobot.id)?.currentExecutionId)
    }

    @Test
    fun `terminal 동일 상태 보고는 최초 배정 Robot ID를 확인한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val assignedRobot = saveRobot(agent.id, execution.id)
        val idleRobot = saveRobot(agent.id, null)

        report(agent.id, execution.id, idleRobot.id, "COMPLETED")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_ROBOT_MISMATCH"))

        report(agent.id, execution.id, assignedRobot.id, "COMPLETED")
            .andExpect(status().isOk)
        assertEquals(null, robotRepository.findById(assignedRobot.id)?.currentExecutionId)
        assertEquals(null, robotRepository.findById(idleRobot.id)?.currentExecutionId)
    }

    @Test
    fun `terminal 동일 상태 재보고는 Robot의 새 점유를 해제하지 않는다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val robot = saveRobot(agent.id, execution.id)

        report(agent.id, execution.id, robot.id, "COMPLETED")
            .andExpect(status().isOk)
        val nextExecution = saveExecution(ExecutionStatus.ASSIGNED)
        inTransaction {
            val reboundRobot = robotRepository.findById(robot.id)!!
            reboundRobot.assignExecution(nextExecution.id)
            robotRepository.save(reboundRobot)
        }

        report(agent.id, execution.id, robot.id, "COMPLETED")
            .andExpect(status().isOk)
        assertEquals(nextExecution.id, robotRepository.findById(robot.id)?.currentExecutionId)
    }

    @Test
    fun `currentExecutionId 불일치와 존재하지 않는 Execution을 거부한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)
        val robot = saveRobot(agent.id, UUID.randomUUID())

        report(agent.id, execution.id, robot.id, "COMPLETED")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_ROBOT_MISMATCH"))

        val missingExecutionId = UUID.randomUUID()
        val missingExecutionRobot = saveRobot(agent.id, missingExecutionId)
        report(agent.id, missingExecutionId, missingExecutionRobot.id, "RUNNING")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_NOT_FOUND"))
    }

    @Test
    fun `Agent와 Robot이 존재하지 않으면 기존 오류 계약을 사용한다`() {
        val agent = saveAgent()
        val execution = saveExecution(ExecutionStatus.RUNNING)

        report(UUID.randomUUID(), execution.id, UUID.randomUUID(), "COMPLETED")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))

        report(agent.id, execution.id, UUID.randomUUID(), "COMPLETED")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"))
    }

    @Test
    fun `잘못된 상태와 허용되지 않은 전이를 거부한다`() {
        val agent = saveAgent()
        val queuedExecution = saveExecution(ExecutionStatus.QUEUED)
        val queuedRobot = saveRobot(agent.id, queuedExecution.id)

        report(agent.id, queuedExecution.id, queuedRobot.id, "RUNNING")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_STATUS_TRANSITION_INVALID"))

        report(agent.id, queuedExecution.id, queuedRobot.id, "CANCELLED")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
    }

    @Test
    fun `terminal 상태를 다른 terminal 상태로 변경할 수 없다`() {
        val agent = saveAgent()
        val completedExecution = saveExecution(ExecutionStatus.COMPLETED)
        val completedRobot = saveRobot(agent.id, completedExecution.id)
        val failedExecution = saveExecution(ExecutionStatus.FAILED)
        val failedRobot = saveRobot(agent.id, failedExecution.id)

        report(agent.id, completedExecution.id, completedRobot.id, "FAILED")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_STATUS_TRANSITION_INVALID"))
        report(agent.id, failedExecution.id, failedRobot.id, "COMPLETED")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_STATUS_TRANSITION_INVALID"))
    }

    @Test
    fun `잘못된 Agent token을 거부한다`() {
        mockMvc.perform(
            post("/api/v1/internal/agents/${UUID.randomUUID()}/executions/${UUID.randomUUID()}/status")
                .header("X-Agent-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"robotId\":\"${UUID.randomUUID()}\",\"status\":\"RUNNING\"}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AGENT_AUTH_INVALID"))
    }

    private fun report(agentId: UUID, executionId: UUID, robotId: UUID, status: String) = mockMvc.perform(
        post("/api/v1/internal/agents/$agentId/executions/$executionId/status")
            .header("X-Agent-Token", agentId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"robotId\":\"$robotId\",\"status\":\"$status\"}"),
    )

    private fun saveAgent(): Agent = inTransaction {
        val agent = Agent.register(
                name = "status-agent-${UUID.randomUUID()}",
                agentVersion = "1.0.0",
                sdkVersion = "2.0.0",
                platform = "linux-arm64",
                registeredAt = Instant.parse("2026-09-14T00:00:00Z"),
            ).apply { rotateCredential(AgentCredentialService.digest(id.toString())) }
        agentRepository.save(agent)
    }

    private fun saveExecution(status: ExecutionStatus): Execution = inTransaction {
        val execution = Execution.create()
        when (status) {
            ExecutionStatus.QUEUED -> Unit
            ExecutionStatus.ASSIGNED -> execution.assign()
            ExecutionStatus.RUNNING -> {
                execution.assign()
                execution.start()
            }
            ExecutionStatus.COMPLETED -> {
                execution.assign()
                execution.start()
                execution.complete()
            }
            ExecutionStatus.FAILED -> {
                execution.assign()
                execution.start()
                execution.fail()
            }
            ExecutionStatus.CANCELLED -> execution.cancel()
        }
        executionRepository.save(execution)
    }

    private fun saveRobot(agentId: UUID, currentExecutionId: UUID?): Robot = inTransaction {
        val robot = robotRepository.save(
            Robot.register(
                alias = "status-robot-${UUID.randomUUID()}",
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
        if (currentExecutionId != null) {
            executionRepository.findById(currentExecutionId)?.let { execution ->
                execution.bindRobot(robot.id)
                executionRepository.save(execution)
            }
        }
        robot
    }

    private fun <T> inTransaction(action: () -> T): T = requireNotNull(
        TransactionTemplate(transactionManager).execute { action() },
    )

    companion object {
        private const val TEST_TOKEN = "test-agent-token"

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("poppy.agent.token") { TEST_TOKEN }
        }
    }
}
