package team.inreok.poppyserver.domain.block.application

import org.junit.jupiter.api.Test
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockProgramValidatorTest {
    private val validator = BlockProgramValidator()

    @Test
    fun `valid program has START END and repeat children`() {
        val repeat = block("repeat", BlockType.REPEAT, BlockParameters.Count(2), listOf(block("stop", BlockType.STOP)))

        val result = validator.validate(
            BlockProgram(1, listOf(block("start", BlockType.START), repeat, block("end", BlockType.END))),
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `validator reports missing and misplaced structural blocks`() {
        val result = validator.validate(BlockProgram(1, listOf(block("stop", BlockType.STOP))))

        assertCodes(
            result,
            BlockProgramValidationErrorCode.START_REQUIRED,
            BlockProgramValidationErrorCode.START_NOT_FIRST,
            BlockProgramValidationErrorCode.END_REQUIRED,
            BlockProgramValidationErrorCode.END_NOT_LAST,
        )
    }

    @Test
    fun `validator reports duplicate ids across top level and children`() {
        val repeat = block(
            "repeat",
            BlockType.REPEAT,
            BlockParameters.Count(2),
            listOf(block("shared", BlockType.STOP)),
        )

        val result = validator.validate(
            BlockProgram(
                1,
                listOf(block("start", BlockType.START), block("shared", BlockType.SIT), repeat, block("end", BlockType.END)),
            ),
        )

        assertCodes(result, BlockProgramValidationErrorCode.DUPLICATE_BLOCK_ID)
        assertEquals("blocks[2].children[0]", result.errors.single { it.code == BlockProgramValidationErrorCode.DUPLICATE_BLOCK_ID }.path)
    }

    @Test
    fun `validator rejects empty and nested repeat and structural children`() {
        val nested = block("nested", BlockType.REPEAT, BlockParameters.Count(1), listOf(block("move", BlockType.STOP)))
        val repeat = block(
            "repeat",
            BlockType.REPEAT,
            BlockParameters.Count(2),
            listOf(nested, block("start-child", BlockType.START), block("end-child", BlockType.END)),
        )
        val empty = block("empty", BlockType.REPEAT, BlockParameters.Count(1), emptyList())

        val result = validator.validate(
            BlockProgram(1, listOf(block("start", BlockType.START), repeat, empty, block("end", BlockType.END))),
        )

        assertCodes(
            result,
            BlockProgramValidationErrorCode.NESTED_REPEAT_NOT_ALLOWED,
            BlockProgramValidationErrorCode.STRUCTURAL_BLOCK_INSIDE_REPEAT,
            BlockProgramValidationErrorCode.REPEAT_CHILDREN_EMPTY,
        )
    }

    private fun assertCodes(result: BlockProgramValidationResult, vararg expected: BlockProgramValidationErrorCode) {
        assertTrue(expected.all { code -> result.errors.any { it.code == code } })
    }

    private fun block(
        id: String,
        type: BlockType,
        parameters: BlockParameters = BlockParameters.None,
        children: List<BlockInstance>? = null,
    ) = BlockInstance(id, type, parameters, children)
}
