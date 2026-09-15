package team.inreok.poppyserver.domain.execution.presentation

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.agent.application.AgentRepository
import team.inreok.poppyserver.domain.agent.application.AgentCredentialService
import team.inreok.poppyserver.domain.agent.model.Agent
import team.inreok.poppyserver.domain.execution.application.AgentExecutionStatusService
import team.inreok.poppyserver.domain.execution.application.ExecutionCancellationService
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.application.ReportExecutionStatus
import team.inreok.poppyserver.domain.execution.application.ReportExecutionStatusCommand
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.robot.application.RobotRepository
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import team.inreok.poppyserver.support.validBlockProgram
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
class ExecutionCancellationIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var agentRepository: AgentRepository

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var robotRepository: RobotRepository

    @Autowired
    lateinit var executionCancellationService: ExecutionCancellationService

    @Autowired
    lateinit var agentExecutionStatusService: AgentExecutionStatusService

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    lateinit var sessionService: SessionService

    private val sessionTokens = mutableMapOf<UUID, String>()

    @Test
    fun `QUEUED Execution을 CANCELLED로 변경한다`() {
        val execution = saveExecution(ExecutionStatus.QUEUED)

        cancel(execution.id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.executionId").value(execution.id.toString()))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))

        assertEquals(ExecutionStatus.CANCELLED, executionRepository.findById(execution.id)?.status)
    }

    @Test
    fun `ASSIGNED Execution 취소 시 Robot 점유를 해제한다`() {
        val fixture = saveAssignedFixture()

        cancel(fixture.execution.id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))

        assertEquals(ExecutionStatus.CANCELLED, executionRepository.findById(fixture.execution.id)?.status)
        assertEquals(fixture.robot.id, executionRepository.findById(fixture.execution.id)?.assignedRobotId)
        assertNull(robotRepository.findById(fixture.robot.id)?.currentExecutionId)
    }

    @Test
    fun `RUNNING Execution은 체험자 취소를 거부한다`() {
        val fixture = saveAssignedFixture(ExecutionStatus.RUNNING)

        cancel(fixture.execution.id)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_CANCELLATION_NOT_ALLOWED"))

        assertEquals(ExecutionStatus.RUNNING, executionRepository.findById(fixture.execution.id)?.status)
        assertEquals(fixture.execution.id, robotRepository.findById(fixture.robot.id)?.currentExecutionId)
    }

    @Test
    fun `COMPLETED와 FAILED Execution은 취소를 거부한다`() {
        val completed = saveAssignedFixture(ExecutionStatus.COMPLETED)
        val failed = saveAssignedFixture(ExecutionStatus.FAILED)

        cancel(completed.execution.id)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_CANCELLATION_NOT_ALLOWED"))
        cancel(failed.execution.id)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_CANCELLATION_NOT_ALLOWED"))

        assertEquals(ExecutionStatus.COMPLETED, executionRepository.findById(completed.execution.id)?.status)
        assertEquals(ExecutionStatus.FAILED, executionRepository.findById(failed.execution.id)?.status)
    }

    @Test
    fun `CANCELLED Execution에 대한 중복 취소 요청은 성공한다`() {
        val execution = saveExecution(ExecutionStatus.CANCELLED)

        cancel(execution.id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
        cancel(execution.id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
    }

    @Test
    fun `존재하지 않는 Execution 취소 요청은 404를 반환한다`() {
        cancel(UUID.randomUUID())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_NOT_FOUND"))
    }

    @Test
    fun `ASSIGNED Execution의 assignedRobotId가 없으면 상태를 변경하지 않는다`() {
        val execution = saveExecution(ExecutionStatus.ASSIGNED)

        cancel(execution.id)
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_CANCELLATION_INVARIANT_VIOLATED"))

        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(execution.id)?.status)
    }

    @Test
    fun `Robot currentExecutionId가 다르면 Execution과 Robot을 변경하지 않는다`() {
        val fixture = saveAssignedFixture(currentExecutionId = UUID.randomUUID())

        cancel(fixture.execution.id)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_ROBOT_MISMATCH"))

        assertEquals(ExecutionStatus.ASSIGNED, executionRepository.findById(fixture.execution.id)?.status)
        assertEquals(fixture.otherExecutionId, robotRepository.findById(fixture.robot.id)?.currentExecutionId)
    }

    @Test
    fun `취소 대상이 아닌 Robot의 점유는 보존한다`() {
        val target = saveAssignedFixture()
        val unrelatedExecutionId = UUID.randomUUID()
        val unrelatedRobot = saveRobot(currentExecutionId = unrelatedExecutionId)

        cancel(target.execution.id).andExpect(status().isOk)

        assertEquals(unrelatedExecutionId, robotRepository.findById(unrelatedRobot.id)?.currentExecutionId)
    }

    @Test
    fun `cancel과 Agent RUNNING 보고의 동시 요청은 한 상태로 일관되게 귀결된다`() {
        val agent = saveAgent()
        val fixture = saveAssignedFixture()
        bindRobotToAgent(fixture.robot.id, agent.id)
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        val cancelFuture = executor.submit<OperationResult> {
            awaitConcurrentStart(ready, start)
            try {
                OperationResult(executionCancellationService.cancel(fixture.execution.id).status, null)
            } catch (exception: ApplicationException) {
                OperationResult(null, exception.errorCode)
            }
        }
        val runningFuture = executor.submit<OperationResult> {
            awaitConcurrentStart(ready, start)
            try {
                OperationResult(
                    agentExecutionStatusService.report(
                        agent.id,
                        fixture.execution.id,
                        ReportExecutionStatusCommand(fixture.robot.id, ReportExecutionStatus.RUNNING),
                    ).status,
                    null,
                )
            } catch (exception: ApplicationException) {
                OperationResult(null, exception.errorCode)
            }
        }
        check(ready.await(10, TimeUnit.SECONDS))
        start.countDown()

        val results = try {
            listOf(cancelFuture.get(30, TimeUnit.SECONDS), runningFuture.get(30, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
        val finalExecution = requireNotNull(executionRepository.findById(fixture.execution.id))
        val finalRobot = requireNotNull(robotRepository.findById(fixture.robot.id))

        assertEquals(1, results.count { it.status != null })
        assertEquals(true, finalExecution.status == ExecutionStatus.CANCELLED || finalExecution.status == ExecutionStatus.RUNNING)
        if (finalExecution.status == ExecutionStatus.CANCELLED) {
            assertNull(finalRobot.currentExecutionId)
        } else {
            assertEquals(fixture.execution.id, finalRobot.currentExecutionId)
        }
    }

    @Test
    fun `legacy provenance Execution cannot be cancelled through Session access`() {
        val execution = inTransaction { executionRepository.save(Execution.create()) }

        cancel(execution.id)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_ACCESS_DENIED"))
    }

    private fun cancel(executionId: UUID) = mockMvc.perform(
        post("/api/v1/executions/$executionId/cancel")
            .header("X-Session-Token", sessionTokens[executionId] ?: ""),
    )

    private fun saveExecution(status: ExecutionStatus): Execution {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, validBlockProgram())
        return inTransaction {
            val execution = Execution.create(session.sessionId, 1)
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
                    execution.fail()
                }
                ExecutionStatus.CANCELLED -> execution.cancel()
            }
            sessionTokens[execution.id] = session.sessionToken
            executionRepository.save(execution)
        }
    }

    private fun saveAssignedFixture(
        status: ExecutionStatus = ExecutionStatus.ASSIGNED,
        currentExecutionId: UUID? = null,
    ): AssignedFixture {
        val session = sessionService.createSession()
        sessionService.appendBlockRevision(session.sessionId, validBlockProgram())
        val execution = Execution.create(session.sessionId, 1)
        val robot = saveRobot(currentExecutionId = currentExecutionId ?: execution.id)
        inTransaction {
            execution.apply {
                assignToRobot(robot.id)
                if (status == ExecutionStatus.RUNNING || status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED) {
                    start()
                }
                if (status == ExecutionStatus.COMPLETED) complete()
                if (status == ExecutionStatus.FAILED) fail()
            }
            executionRepository.save(execution)
        }
        sessionTokens[execution.id] = session.sessionToken
        return AssignedFixture(execution, robot, currentExecutionId ?: execution.id)
    }

    private fun saveRobot(currentExecutionId: UUID?): Robot = inTransaction {
        robotRepository.save(
            Robot.register(
                alias = "cancel-robot-${UUID.randomUUID()}",
                model = "GO2",
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

    private fun saveAgent(): Agent = inTransaction {
        val agent = Agent.register(
                name = "cancel-agent-${UUID.randomUUID()}",
                agentVersion = "1.0.0",
                sdkVersion = "2.0.0",
                platform = "linux-arm64",
                registeredAt = Instant.parse("2026-09-14T00:00:00Z"),
            ).apply { rotateCredential(AgentCredentialService.digest(id.toString())) }
        agentRepository.save(agent)
    }

    private fun bindRobotToAgent(robotId: UUID, agentId: UUID) {
        inTransaction {
            val robot = requireNotNull(robotRepository.findById(robotId))
            robot.bindToAgent(agentId)
            robotRepository.save(robot)
        }
    }

    private fun awaitConcurrentStart(ready: CountDownLatch, start: CountDownLatch) {
        ready.countDown()
        check(ready.await(10, TimeUnit.SECONDS))
        check(start.await(10, TimeUnit.SECONDS))
    }

    private fun <T> inTransaction(action: () -> T): T = requireNotNull(
        TransactionTemplate(transactionManager).execute { action() },
    )

    private data class AssignedFixture(
        val execution: Execution,
        val robot: Robot,
        val otherExecutionId: UUID,
    )

    private data class OperationResult(
        val status: ExecutionStatus?,
        val errorCode: ErrorCode?,
    )
}
