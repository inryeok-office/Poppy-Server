package team.inreok.poppyserver.domain.mission.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType

class MissionCompletionRuleTest {

    @Test
    fun `필수 블록이 없으면 MISSING_REQUIRED_BLOCK`() {
        val rule = MissionCompletionRule(requiredBlocks = listOf(setOf(BlockType.TURN_LEFT, BlockType.TURN_RIGHT)))

        val result = rule.evaluate(program(block(BlockType.STOP)))

        assertFalse(result.passed)
        assertEquals(listOf(MissionFailureReason(MissionFailureCode.MISSING_REQUIRED_BLOCK, "TURN_LEFT|TURN_RIGHT")), result.reasons)
    }

    @Test
    fun `필수 블록은 REPEAT 안에 있어도 인정한다`() {
        val rule = MissionCompletionRule(requiredBlocks = listOf(setOf(BlockType.TURN_RIGHT)))

        val result = rule.evaluate(program(repeat(2, block(BlockType.TURN_RIGHT))))

        assertTrue(result.passed)
    }

    @Test
    fun `금지 블록이 있으면 FORBIDDEN_BLOCK`() {
        val rule = MissionCompletionRule(forbiddenBlocks = setOf(BlockType.MOVE_FORWARD))

        val result = rule.evaluate(program(block(BlockType.MOVE_FORWARD), block(BlockType.MOVE_FORWARD)))

        assertEquals(listOf(MissionFailureReason(MissionFailureCode.FORBIDDEN_BLOCK, "MOVE_FORWARD")), result.reasons)
    }

    @Test
    fun `순서 조건은 실행 순서의 부분 수열로 판정한다`() {
        val rule = MissionCompletionRule(
            requiredOrder = listOf(setOf(BlockType.MOVE_FORWARD), setOf(BlockType.STOP), setOf(BlockType.PRESET)),
        )

        assertTrue(rule.evaluate(program(block(BlockType.MOVE_FORWARD), block(BlockType.TURN_LEFT), block(BlockType.STOP), block(BlockType.PRESET))).passed)
        val reversed = rule.evaluate(program(block(BlockType.PRESET), block(BlockType.MOVE_FORWARD), block(BlockType.STOP)))
        assertEquals(MissionFailureCode.ORDER_NOT_SATISFIED, reversed.reasons.single().code)
    }

    @Test
    fun `최소 실행 횟수는 REPEAT 를 펼쳐서 센다`() {
        val rule = MissionCompletionRule(minimumExecutions = listOf(MinimumExecutions(setOf(BlockType.MOVE_FORWARD), 4)))

        assertTrue(rule.evaluate(program(repeat(4, block(BlockType.MOVE_FORWARD), block(BlockType.TURN_LEFT)))).passed)
        val short = rule.evaluate(program(repeat(3, block(BlockType.MOVE_FORWARD))))
        assertEquals(MissionFailureReason(MissionFailureCode.TOO_FEW_EXECUTIONS, "MOVE_FORWARD x4"), short.reasons.single())
    }

    @Test
    fun `마지막 동작은 WAIT 와 START END 를 건너뛰고 본다`() {
        val rule = MissionCompletionRule(lastAction = BlockType.STOP)

        assertTrue(rule.evaluate(program(block(BlockType.MOVE_FORWARD), block(BlockType.STOP), block(BlockType.WAIT))).passed)
        val result = rule.evaluate(program(block(BlockType.STOP), block(BlockType.MOVE_FORWARD)))
        assertEquals(MissionFailureReason(MissionFailureCode.LAST_ACTION_MISMATCH, "STOP"), result.reasons.single())
    }

    @Test
    fun `최대 블록 수는 START END 를 빼고 배치된 블록 수로 센다`() {
        val rule = MissionCompletionRule(maxBlockCount = 3)

        assertTrue(rule.evaluate(program(repeat(4, block(BlockType.MOVE_FORWARD), block(BlockType.TURN_LEFT)))).passed)
        val result = rule.evaluate(program(block(BlockType.MOVE_FORWARD), block(BlockType.TURN_LEFT), block(BlockType.MOVE_FORWARD), block(BlockType.STOP)))
        assertEquals(MissionFailureReason(MissionFailureCode.TOO_MANY_BLOCKS, "4 > 3"), result.reasons.single())
    }

    @Test
    fun `여러 조건을 못 채우면 사유를 모두 반환한다`() {
        val rule = MissionCompletionRule(
            requiredBlocks = listOf(setOf(BlockType.REPEAT)),
            lastAction = BlockType.STOP,
        )

        val result = rule.evaluate(program(block(BlockType.MOVE_FORWARD)))

        assertEquals(
            listOf(MissionFailureCode.MISSING_REQUIRED_BLOCK, MissionFailureCode.LAST_ACTION_MISMATCH),
            result.reasons.map { it.code },
        )
    }

    private var sequence = 0

    private fun block(type: BlockType): BlockInstance = BlockInstance(
        id = "b${sequence++}",
        type = type,
        parameters = BlockParameters.None,
    )

    private fun repeat(count: Long, vararg children: BlockInstance): BlockInstance = BlockInstance(
        id = "r${sequence++}",
        type = BlockType.REPEAT,
        parameters = BlockParameters.Count(count),
        children = children.toList(),
    )

    private fun program(vararg blocks: BlockInstance): BlockProgram = BlockProgram(
        schemaVersion = 1,
        blocks = listOf(block(BlockType.START)) + blocks.toList() + block(BlockType.END),
    )
}
