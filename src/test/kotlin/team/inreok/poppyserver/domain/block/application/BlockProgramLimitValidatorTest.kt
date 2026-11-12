package team.inreok.poppyserver.domain.block.application

import org.junit.jupiter.api.Test
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockProgramLimitValidatorTest {
    private val validator = BlockProgramLimitValidator()

    @Test
    fun `parameters outside the execution ranges are rejected`() {
        listOf(
            block("move", BlockType.MOVE_FORWARD, BlockParameters.DistanceMeters(3.1)),
            block("move", BlockType.MOVE_BACKWARD, BlockParameters.DistanceMeters(0.05)),
            block("turn", BlockType.TURN_LEFT, BlockParameters.AngleDegrees(361.0)),
            block("turn", BlockType.TURN_RIGHT, BlockParameters.AngleDegrees(0.5)),
            block("wait", BlockType.WAIT, BlockParameters.DurationSeconds(10.5)),
        ).forEach { invalid ->
            assertCodes(validator.validate(program(invalid)), BlockProgramValidationErrorCode.PARAMETER_OUT_OF_RANGE)
        }
        val tooManyRepeats = block(
            "repeat",
            BlockType.REPEAT,
            BlockParameters.Count(11),
            listOf(block("wait", BlockType.WAIT, BlockParameters.DurationSeconds(1.0))),
        )
        assertCodes(validator.validate(program(tooManyRepeats)), BlockProgramValidationErrorCode.PARAMETER_OUT_OF_RANGE)
    }

    @Test
    fun `boundary values are accepted`() {
        val result = validator.validate(
            program(
                block("m1", BlockType.MOVE_FORWARD, BlockParameters.DistanceMeters(3.0)),
                block("m2", BlockType.MOVE_BACKWARD, BlockParameters.DistanceMeters(1.0)),
                block("t1", BlockType.TURN_RIGHT, BlockParameters.AngleDegrees(360.0)),
                block("w1", BlockType.WAIT, BlockParameters.DurationSeconds(10.0)),
            ),
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `only allow-listed preset codes are accepted`() {
        listOf("HELLO", "HEART", "DANCE1", "STRETCH").forEach { code ->
            assertTrue(validator.validate(program(block("p", BlockType.PRESET, BlockParameters.PresetCode(code)))).isValid)
        }
        listOf("FRONTFLIP", "hello", "demo").forEach { code ->
            assertCodes(
                validator.validate(program(block("p", BlockType.PRESET, BlockParameters.PresetCode(code)))),
                BlockProgramValidationErrorCode.PRESET_CODE_NOT_ALLOWED,
            )
        }
    }

    @Test
    fun `total distance over four meters including repeats is rejected`() {
        val patrol = { count: Long ->
            block(
                "repeat",
                BlockType.REPEAT,
                BlockParameters.Count(count),
                listOf(block("m", BlockType.MOVE_FORWARD, BlockParameters.DistanceMeters(0.5))),
            )
        }

        assertTrue(validator.validate(program(patrol(8))).isValid)
        assertCodes(validator.validate(program(patrol(9))), BlockProgramValidationErrorCode.TOTAL_DISTANCE_EXCEEDED)
    }

    @Test
    fun `total motion time over 120 seconds is rejected and blocks after STOP are not counted`() {
        val waits = { count: Long ->
            block(
                "repeat",
                BlockType.REPEAT,
                BlockParameters.Count(count),
                listOf(
                    block("w1", BlockType.WAIT, BlockParameters.DurationSeconds(10.0)),
                    block("w2", BlockType.WAIT, BlockParameters.DurationSeconds(2.0)),
                ),
            )
        }

        assertTrue(validator.validate(program(waits(10))).isValid)
        assertCodes(
            validator.validate(program(waits(10), block("sit", BlockType.SIT))),
            BlockProgramValidationErrorCode.TOTAL_DURATION_EXCEEDED,
        )
        assertTrue(
            validator.validate(program(block("stop", BlockType.STOP), waits(10), block("sit", BlockType.SIT))).isValid,
        )
    }

    @Test
    fun `motion time uses Agent rates for move turn posture and preset`() {
        val tight = program(
            block("m", BlockType.MOVE_FORWARD, BlockParameters.DistanceMeters(3.0)),
            block("t", BlockType.TURN_LEFT, BlockParameters.AngleDegrees(360.0)),
            block("s", BlockType.SIT),
            block("u", BlockType.STAND),
            block("p", BlockType.PRESET, BlockParameters.PresetCode("HELLO")),
            block(
                "r",
                BlockType.REPEAT,
                BlockParameters.Count(10),
                listOf(block("w", BlockType.WAIT, BlockParameters.DurationSeconds(9.0))),
            ),
            block("w2", BlockType.WAIT, BlockParameters.DurationSeconds(3.0)),
        )
        assertTrue(validator.validate(tight).isValid)
    }

    private fun program(vararg body: BlockInstance): BlockProgram =
        BlockProgram(1, listOf(block("start", BlockType.START), *body, block("end", BlockType.END)))

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

class BlockProgramLimitValidatorArenaTest {
    private val booth = team.inreok.poppyserver.domain.arena.application.ArenaSettingsProvider {
        team.inreok.poppyserver.domain.arena.model.ArenaPresets.ALL.getValue(
            team.inreok.poppyserver.domain.arena.model.ArenaPresets.BOOTH_3X3,
        )
    }

    private fun move(meters: Double) = BlockProgram(
        schemaVersion = 1,
        blocks = listOf(
            BlockInstance(id = "m", type = BlockType.MOVE_FORWARD, parameters = BlockParameters.DistanceMeters(meters)),
        ),
    )

    @Test
    fun `3x3 부스에서는 1회 이동 1_2m 까지만 허용한다`() {
        val validator = BlockProgramLimitValidator(booth)
        assertTrue(validator.validate(move(1.2)).isValid)
        assertFalse(validator.validate(move(1.3)).isValid)
        assertFalse(validator.validate(move(3.0)).isValid)
    }

    @Test
    fun `설정이 없으면(기본 6x6) 3m 까지 허용하는 기존 동작이 그대로다`() {
        val validator = BlockProgramLimitValidator()
        assertTrue(validator.validate(move(3.0)).isValid)
        assertFalse(validator.validate(move(3.1)).isValid)
    }

    @Test
    fun `블록 정의 카탈로그의 이동 최대값도 공간을 따른다`() {
        fun max(catalog: BlockDefinitionCatalog) = catalog.list().first { it.type == BlockType.MOVE_FORWARD }.parameters.single().max
        assertEquals(1.2, max(BlockDefinitionCatalog(booth)))
        assertEquals(3.0, max(BlockDefinitionCatalog()))
    }
}
