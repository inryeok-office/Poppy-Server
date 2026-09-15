package team.inreok.poppyserver.domain.execution.presentation

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest

@SpringBootTest
@AutoConfigureMockMvc
class ExecutionStatusIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `queued execution returns read model and queue position`() {
        val session = sessionWithRevision()
        val execution = saveExecution(Execution.create(session.sessionId, 1))

        mockMvc.perform(
            get("/api/v1/executions/${execution.id}")
                .header("X-Session-Token", session.sessionToken)
                .accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.executionId").value(execution.id.toString()))
            .andExpect(jsonPath("$.data.sessionId").value(session.sessionId.toString()))
            .andExpect(jsonPath("$.data.blockVersion").value(1))
            .andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andExpect(jsonPath("$.data.queuePosition").isNumber)
            .andExpect(jsonPath("$.data.assignedRobotId").value(null))
            .andExpect(jsonPath("$.data.queuedAt").isNotEmpty)
            .andExpect(jsonPath("$.data.startedAt").value(null))
            .andExpect(jsonPath("$.data.finishedAt").value(null))
    }

    @Test
    fun `all execution lifecycle states are exposed`() {
        val statuses = listOf(
            ExecutionStatus.ASSIGNED,
            ExecutionStatus.RUNNING,
            ExecutionStatus.COMPLETED,
            ExecutionStatus.FAILED,
            ExecutionStatus.CANCELLED,
        )

        statuses.forEach { expectedStatus ->
            val session = sessionWithRevision()
            val execution = Execution.create(session.sessionId, 1)
            val robotId = UUID.randomUUID()
            when (expectedStatus) {
                ExecutionStatus.ASSIGNED -> execution.assignToRobot(robotId)
                ExecutionStatus.RUNNING -> {
                    execution.assignToRobot(robotId)
                    execution.start()
                }
                ExecutionStatus.COMPLETED -> {
                    execution.assignToRobot(robotId)
                    execution.start()
                    execution.complete()
                }
                ExecutionStatus.FAILED -> {
                    execution.assignToRobot(robotId)
                    execution.start()
                    execution.fail()
                }
                ExecutionStatus.CANCELLED -> execution.cancel()
                ExecutionStatus.QUEUED -> error("queued state is covered separately")
            }
            saveExecution(execution)

            val result = mockMvc.perform(
                get("/api/v1/executions/${execution.id}")
                    .header("X-Session-Token", session.sessionToken)
                    .accept(MediaType.APPLICATION_JSON),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value(expectedStatus.name))
                .andExpect(jsonPath("$.data.queuePosition").value(null))
                .andExpect(jsonPath("$.data.assignedRobotId").value(if (expectedStatus == ExecutionStatus.CANCELLED) null else robotId.toString()))

            if (expectedStatus == ExecutionStatus.RUNNING ||
                expectedStatus == ExecutionStatus.COMPLETED ||
                expectedStatus == ExecutionStatus.FAILED
            ) {
                result.andExpect(jsonPath("$.data.startedAt").exists())
            } else {
                result.andExpect(jsonPath("$.data.startedAt").value(null))
            }
            if (expectedStatus.isTerminal()) {
                result.andExpect(jsonPath("$.data.finishedAt").exists())
            } else {
                result.andExpect(jsonPath("$.data.finishedAt").value(null))
            }
        }
    }

    @Test
    fun `unknown execution is not exposed`() {
        val session = sessionService.createSession()

        mockMvc.perform(
            get("/api/v1/executions/${UUID.randomUUID()}")
                .header("X-Session-Token", session.sessionToken),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_NOT_FOUND"))
    }

    @Test
    fun `missing token is unauthorized even for unknown execution`() {
        mockMvc.perform(get("/api/v1/executions/${UUID.randomUUID()}"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))
    }

    @Test
    fun `missing or invalid token is unauthorized`() {
        val session = sessionWithRevision()
        val execution = saveExecution(Execution.create(session.sessionId, 1))

        mockMvc.perform(get("/api/v1/executions/${execution.id}"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))

        mockMvc.perform(
            get("/api/v1/executions/${execution.id}")
                .header("X-Session-Token", "invalid-token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_TOKEN_INVALID"))
    }

    @Test
    fun `another session token is forbidden`() {
        val owner = sessionWithRevision()
        val other = sessionWithRevision()
        val execution = saveExecution(Execution.create(owner.sessionId, 1))

        mockMvc.perform(
            get("/api/v1/executions/${execution.id}")
                .header("X-Session-Token", other.sessionToken),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_ACCESS_DENIED"))
    }

    @Test
    fun `legacy execution cannot be accessed through session api`() {
        val session = sessionService.createSession()
        val execution = saveExecution(Execution.create())

        mockMvc.perform(
            get("/api/v1/executions/${execution.id}")
                .header("X-Session-Token", session.sessionToken),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("EXECUTION_ACCESS_DENIED"))
    }

    private fun saveExecution(execution: Execution): Execution = requireNotNull(
        TransactionTemplate(transactionManager).execute { executionRepository.save(execution) },
    )

    private fun sessionWithRevision() = sessionService.createSession().also {
        sessionService.appendBlockRevision(it.sessionId, "{\"blocks\":[]}")
    }
}

private fun ExecutionStatus.isTerminal(): Boolean = this == ExecutionStatus.COMPLETED ||
    this == ExecutionStatus.FAILED ||
    this == ExecutionStatus.CANCELLED
