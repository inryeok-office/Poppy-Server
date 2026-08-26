package team.inreok.poppyserver.domain.execution.infrastructure

import java.util.UUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.execution.application.ExecutionRepository
import team.inreok.poppyserver.domain.execution.model.Execution
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@SpringBootTest
class ExecutionPersistenceIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @ParameterizedTest
    @EnumSource(ExecutionStatus::class)
    @Transactional
    fun `모든 Execution 상태를 저장하고 조회한다`(status: ExecutionStatus) {
        val execution = executionAt(status)

        executionRepository.save(execution)

        val restored = executionRepository.findById(execution.id)
        assertEquals(execution.id, restored?.id)
        assertEquals(status, restored?.status)
    }

    @Test
    @Transactional
    fun `존재하지 않는 Execution id 조회는 null을 반환한다`() {
        assertNull(executionRepository.findById(UUID.randomUUID()))
    }

    @ParameterizedTest
    @EnumSource(value = ExecutionStatus::class, names = ["COMPLETED", "FAILED", "CANCELLED"])
    @Transactional
    fun `terminal 상태를 복원하면 추가 상태 전이를 거부한다`(status: ExecutionStatus) {
        val execution = executionAt(status)
        executionRepository.save(execution)

        val restored = requireNotNull(executionRepository.findById(execution.id))

        when (status) {
            ExecutionStatus.COMPLETED -> assertFailsWith<IllegalStateException> { restored.start() }
            ExecutionStatus.FAILED -> assertFailsWith<IllegalStateException> { restored.assign() }
            ExecutionStatus.CANCELLED -> assertFailsWith<IllegalStateException> { restored.start() }
            else -> error("terminal status required")
        }
    }

    private fun executionAt(status: ExecutionStatus): Execution = Execution.create().apply {
        when (status) {
            ExecutionStatus.QUEUED -> Unit
            ExecutionStatus.ASSIGNED -> assign()
            ExecutionStatus.RUNNING -> {
                assign()
                start()
            }
            ExecutionStatus.COMPLETED -> {
                assign()
                start()
                complete()
            }
            ExecutionStatus.FAILED -> {
                assign()
                fail()
            }
            ExecutionStatus.CANCELLED -> cancel()
        }
    }
}
