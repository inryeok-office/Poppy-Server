package team.inreok.poppyserver.domain.execution.model

import java.util.UUID
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExecutionTest {

    @Test
    fun `생성 시 대기 상태로 시작한다`() {
        val execution = Execution.create()

        assertEquals(ExecutionStatus.QUEUED, execution.status)
    }

    @Test
    fun `정상 실행 흐름은 대기 배정 실행 완료 순서로 전환한다`() {
        val execution = Execution.create()

        execution.assign()
        execution.start()
        execution.complete()

        assertEquals(ExecutionStatus.COMPLETED, execution.status)
    }

    @Test
    fun `배정 상태에서 실패할 수 있다`() {
        val execution = Execution.create()
        execution.assign()

        execution.fail()

        assertEquals(ExecutionStatus.FAILED, execution.status)
    }

    @Test
    fun `실행 상태에서 실패할 수 있다`() {
        val execution = Execution.create()
        execution.assign()
        execution.start()

        execution.fail()

        assertEquals(ExecutionStatus.FAILED, execution.status)
    }

    @Test
    fun `대기 배정 실행 상태에서 취소할 수 있다`() {
        val queued = Execution.create()
        val assigned = Execution.create().apply { assign() }
        val running = Execution.create().apply {
            assign()
            start()
        }

        queued.cancel()
        assigned.cancel()
        running.cancel()

        assertEquals(ExecutionStatus.CANCELLED, queued.status)
        assertEquals(ExecutionStatus.CANCELLED, assigned.status)
        assertEquals(ExecutionStatus.CANCELLED, running.status)
    }

    @Test
    fun `허용되지 않은 상태 전환을 거부한다`() {
        val execution = Execution.create()

        assertFailsWith<IllegalStateException> { execution.start() }
        assertFailsWith<IllegalStateException> { execution.complete() }
        assertFailsWith<IllegalStateException> { execution.fail() }
    }

    @Test
    fun `terminal 상태에서는 추가 전환을 거부한다`() {
        val completed = Execution.create().apply {
            assign()
            start()
            complete()
        }
        val failed = Execution.create().apply {
            assign()
            fail()
        }
        val cancelled = Execution.create().apply { cancel() }

        assertFailsWith<IllegalStateException> { completed.start() }
        assertFailsWith<IllegalStateException> { failed.assign() }
        assertFailsWith<IllegalStateException> { cancelled.cancel() }
    }

    @ParameterizedTest
    @EnumSource(value = ExecutionStatus::class, names = ["COMPLETED", "FAILED", "CANCELLED"])
    fun `terminal 상태로 복원된 경우 추가 전이를 거부한다`(status: ExecutionStatus) {
        val execution = Execution.restore(UUID.randomUUID(), status)

        when (status) {
            ExecutionStatus.COMPLETED -> assertFailsWith<IllegalStateException> { execution.start() }
            ExecutionStatus.FAILED -> assertFailsWith<IllegalStateException> { execution.assign() }
            ExecutionStatus.CANCELLED -> assertFailsWith<IllegalStateException> { execution.start() }
            else -> error("terminal status required")
        }
    }
}
