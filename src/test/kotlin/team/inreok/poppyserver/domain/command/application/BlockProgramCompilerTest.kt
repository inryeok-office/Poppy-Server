package team.inreok.poppyserver.domain.command.application

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import team.inreok.poppyserver.domain.block.application.BlockProgramValidator
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType
import team.inreok.poppyserver.domain.command.model.CommandType
import team.inreok.poppyserver.domain.command.model.HighLevelCommandParameters
import team.inreok.poppyserver.domain.command.model.MoveDirection
import team.inreok.poppyserver.domain.command.model.Posture
import team.inreok.poppyserver.domain.command.model.TurnDirection
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlockProgramCompilerTest {
    private val objectMapper = JsonMapper.builder().build()
    private val compiler = BlockProgramCompiler(BlockProgramValidator())
    private val serializer = HighLevelCommandProtocolSerializer(objectMapper)
    private val parser = HighLevelCommandProtocolParser(objectMapper)

    @Test
    fun `START and END compile to an empty command list`() {
        val result = compiler.compile(program(block("start", BlockType.START), block("end", BlockType.END)))

        assertEquals(emptyList(), result.commands)
    }

    @Test
    fun `all executable block types map to normalized typed commands`() {
        val result = compiler.compile(
            program(
                block("start", BlockType.START),
                block("wait", BlockType.WAIT, BlockParameters.DurationSeconds(1.5)),
                block("forward", BlockType.MOVE_FORWARD, BlockParameters.DistanceMeters(2.0)),
                block("backward", BlockType.MOVE_BACKWARD, BlockParameters.DistanceMeters(3.0)),
                block("left", BlockType.TURN_LEFT, BlockParameters.AngleDegrees(90.0)),
                block("right", BlockType.TURN_RIGHT, BlockParameters.AngleDegrees(45.0)),
                block("stop", BlockType.STOP),
                block("sit", BlockType.SIT),
                block("stand", BlockType.STAND),
                block("preset", BlockType.PRESET, BlockParameters.PresetCode("demo")),
                block("end", BlockType.END),
            ),
        )

        assertEquals(
            listOf(
                CommandType.WAIT to HighLevelCommandParameters.Wait(1.5),
                CommandType.MOVE to HighLevelCommandParameters.Move(MoveDirection.FORWARD, 2.0),
                CommandType.MOVE to HighLevelCommandParameters.Move(MoveDirection.BACKWARD, 3.0),
                CommandType.TURN to HighLevelCommandParameters.Turn(TurnDirection.LEFT, 90.0),
                CommandType.TURN to HighLevelCommandParameters.Turn(TurnDirection.RIGHT, 45.0),
                CommandType.STOP to HighLevelCommandParameters.None,
                CommandType.POSTURE to HighLevelCommandParameters.PostureCommand(Posture.SIT),
                CommandType.POSTURE to HighLevelCommandParameters.PostureCommand(Posture.STAND),
                CommandType.PRESET to HighLevelCommandParameters.Preset("demo"),
            ),
            result.commands.map { it.type to it.parameters },
        )
        assertEquals(
            listOf("wait", "forward", "backward", "left", "right", "stop", "sit", "stand", "preset"),
            result.commands.map { it.sourceBlockId },
        )
        assertEquals((0 until result.commands.size).toList(), result.commands.map { it.sequence })
    }

    @Test
    fun `REPEAT expands children in order and preserves source block ids`() {
        val repeat = block(
            "repeat",
            BlockType.REPEAT,
            BlockParameters.Count(2),
            listOf(
                block("move", BlockType.MOVE_FORWARD, BlockParameters.DistanceMeters(1.0)),
                block("turn", BlockType.TURN_LEFT, BlockParameters.AngleDegrees(90.0)),
            ),
        )

        val result = compiler.compile(program(block("start", BlockType.START), repeat, block("end", BlockType.END)))

        assertEquals(listOf("move", "turn", "move", "turn"), result.commands.map { it.sourceBlockId })
        assertEquals((0..3).toList(), result.commands.map { it.sequence })
    }

    @Test
    fun `same input produces the same command program`() {
        val input = program(
            block("start", BlockType.START),
            block("wait", BlockType.WAIT, BlockParameters.DurationSeconds(2.25)),
            block("end", BlockType.END),
        )

        assertEquals(compiler.compile(input), compiler.compile(input))
    }

    @Test
    fun `compiler rejects semantically invalid programs without bypassing validator`() {
        val exception = assertFailsWith<BlockProgramCompileException> {
            compiler.compile(program(block("stop", BlockType.STOP)))
        }

        assertEquals(BlockProgramCompileErrorCode.INVALID_PROGRAM, exception.code)
        assertTrue(exception.validationErrors.isNotEmpty())
    }

    @Test
    fun `compiler rejects an unsupported schema version`() {
        val exception = assertFailsWith<BlockProgramCompileException> {
            compiler.compile(BlockProgram(2, listOf(block("start", BlockType.START), block("end", BlockType.END))))
        }

        assertEquals(BlockProgramCompileErrorCode.UNSUPPORTED_SCHEMA_VERSION, exception.code)
    }

    @Test
    fun `negative repeat count is rejected`() {
        val exception = assertFailsWith<BlockProgramCompileException> {
            compiler.compile(
                program(
                    block("start", BlockType.START),
                    block("repeat", BlockType.REPEAT, BlockParameters.Count(-1), listOf(block("stop", BlockType.STOP))),
                    block("end", BlockType.END),
                ),
            )
        }

        assertEquals(BlockProgramCompileErrorCode.INVALID_REPEAT_COUNT, exception.code)
    }

    @Test
    fun `repeat expansion rejects an output size that cannot fit the command list`() {
        val exception = assertFailsWith<BlockProgramCompileException> {
            compiler.compile(
                program(
                    block("start", BlockType.START),
                    block("repeat", BlockType.REPEAT, BlockParameters.Count(Long.MAX_VALUE), listOf(block("stop", BlockType.STOP))),
                    block("end", BlockType.END),
                ),
            )
        }

        assertEquals(BlockProgramCompileErrorCode.COMMAND_COUNT_OVERFLOW, exception.code)
    }

    @Test
    fun `compiled output round trips through the command protocol`() {
        val compiled = compiler.compile(
            program(
                block("start", BlockType.START),
                block("move", BlockType.MOVE_FORWARD, BlockParameters.DistanceMeters(1.25)),
                block("end", BlockType.END),
            ),
        )

        assertEquals(compiled, parser.parse(serializer.serialize(compiled)))
    }

    private fun program(vararg blocks: BlockInstance) = BlockProgram(1, blocks.toList())

    private fun block(
        id: String,
        type: BlockType,
        parameters: BlockParameters = BlockParameters.None,
        children: List<BlockInstance>? = null,
    ) = BlockInstance(id, type, parameters, children)

    private fun assertTrue(value: Boolean) {
        kotlin.test.assertTrue(value)
    }
}
