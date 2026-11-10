package team.inreok.poppyserver.domain.block.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType

@Component
class BlockProgramLimitValidator {
    fun validate(program: BlockProgram): BlockProgramValidationResult {
        val errors = mutableListOf<BlockProgramValidationError>()
        collectParameterErrors(program.blocks, "blocks", errors)

        if (errors.isEmpty()) {
            val totals = ExecutionTotals().also { it.accumulate(program.blocks) }
            if (totals.meters > BlockProgramLimits.MAX_TOTAL_DISTANCE_METERS + TOLERANCE) {
                errors += BlockProgramValidationError(
                    code = BlockProgramValidationErrorCode.TOTAL_DISTANCE_EXCEEDED,
                    message = "total MOVE distance must not exceed ${BlockProgramLimits.MAX_TOTAL_DISTANCE_METERS} meters",
                )
            }
            if (totals.seconds > BlockProgramLimits.MAX_TOTAL_SECONDS + TOLERANCE) {
                errors += BlockProgramValidationError(
                    code = BlockProgramValidationErrorCode.TOTAL_DURATION_EXCEEDED,
                    message = "total motion time must not exceed ${BlockProgramLimits.MAX_TOTAL_SECONDS} seconds",
                )
            }
        }

        return BlockProgramValidationResult(errors)
    }

    private fun collectParameterErrors(
        blocks: List<BlockInstance>,
        path: String,
        errors: MutableList<BlockProgramValidationError>,
    ) {
        blocks.forEachIndexed { index, block ->
            val blockPath = "$path[$index]"
            parameterError(block, blockPath)?.let { errors += it }
            block.children?.let { collectParameterErrors(it, "$blockPath.children", errors) }
        }
    }

    private fun parameterError(block: BlockInstance, path: String): BlockProgramValidationError? {
        val parameters = block.parameters
        val inRange = when {
            block.type in MOVE_TYPES && parameters is BlockParameters.DistanceMeters ->
                parameters.value in BlockProgramLimits.MIN_DISTANCE_METERS..BlockProgramLimits.MAX_DISTANCE_METERS
            block.type in TURN_TYPES && parameters is BlockParameters.AngleDegrees ->
                parameters.value in BlockProgramLimits.MIN_ANGLE_DEGREES..BlockProgramLimits.MAX_ANGLE_DEGREES
            block.type == BlockType.WAIT && parameters is BlockParameters.DurationSeconds ->
                parameters.value in BlockProgramLimits.MIN_WAIT_SECONDS..BlockProgramLimits.MAX_WAIT_SECONDS
            block.type == BlockType.REPEAT && parameters is BlockParameters.Count ->
                parameters.value in BlockProgramLimits.MIN_REPEAT_COUNT..BlockProgramLimits.MAX_REPEAT_COUNT
            block.type == BlockType.PRESET && parameters is BlockParameters.PresetCode ->
                return if (parameters.value in BlockProgramLimits.PRESET_OPTIONS) {
                    null
                } else {
                    BlockProgramValidationError(
                        code = BlockProgramValidationErrorCode.PRESET_CODE_NOT_ALLOWED,
                        blockId = block.id,
                        path = path,
                        message = "presetCode must be one of ${BlockProgramLimits.PRESET_OPTIONS.keys}",
                    )
                }
            else -> true
        }
        if (inRange) return null
        return BlockProgramValidationError(
            code = BlockProgramValidationErrorCode.PARAMETER_OUT_OF_RANGE,
            blockId = block.id,
            path = path,
            message = "${block.type.parameterName} is outside the allowed range",
        )
    }

    private class ExecutionTotals {
        var meters = 0.0
        var seconds = 0.0
        private var stopped = false

        fun accumulate(blocks: List<BlockInstance>) {
            for (block in blocks) {
                if (stopped) return
                val parameters = block.parameters
                when {
                    block.type == BlockType.STOP -> stopped = true
                    block.type == BlockType.REPEAT && parameters is BlockParameters.Count ->
                        repeat(parameters.value.toInt()) { accumulate(block.children.orEmpty()) }
                    parameters is BlockParameters.DistanceMeters -> {
                        meters += parameters.value
                        seconds += parameters.value / BlockProgramLimits.LINEAR_SPEED_METERS_PER_SECOND
                    }
                    parameters is BlockParameters.AngleDegrees ->
                        seconds += parameters.value / BlockProgramLimits.TURN_RATE_DEGREES_PER_SECOND
                    parameters is BlockParameters.DurationSeconds -> seconds += parameters.value
                    block.type == BlockType.SIT || block.type == BlockType.STAND ->
                        seconds += BlockProgramLimits.POSTURE_SETTLE_SECONDS
                    block.type == BlockType.PRESET -> seconds += BlockProgramLimits.PRESET_SETTLE_SECONDS
                }
            }
        }
    }

    private companion object {
        const val TOLERANCE = 1e-9
        val MOVE_TYPES = setOf(BlockType.MOVE_FORWARD, BlockType.MOVE_BACKWARD)
        val TURN_TYPES = setOf(BlockType.TURN_LEFT, BlockType.TURN_RIGHT)
    }
}
