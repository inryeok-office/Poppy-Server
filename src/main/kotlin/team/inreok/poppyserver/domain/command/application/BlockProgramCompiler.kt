package team.inreok.poppyserver.domain.command.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.block.application.BlockProgramValidationError
import team.inreok.poppyserver.domain.block.application.BlockProgramValidator
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType
import team.inreok.poppyserver.domain.command.model.CommandType
import team.inreok.poppyserver.domain.command.model.HighLevelCommand
import team.inreok.poppyserver.domain.command.model.HighLevelCommandParameters
import team.inreok.poppyserver.domain.command.model.HighLevelCommandProgram
import team.inreok.poppyserver.domain.command.model.MoveDirection
import team.inreok.poppyserver.domain.command.model.Posture
import team.inreok.poppyserver.domain.command.model.TurnDirection

@Component
class BlockProgramCompiler(
    private val blockProgramValidator: BlockProgramValidator,
) {
    fun compile(program: BlockProgram): HighLevelCommandProgram {
        if (program.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw BlockProgramCompileException(
                code = BlockProgramCompileErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                detail = "schemaVersion ${program.schemaVersion} is not supported",
            )
        }

        val validationErrors = blockProgramValidator.validate(program).errors
        if (validationErrors.isNotEmpty()) {
            throw BlockProgramCompileException(
                code = BlockProgramCompileErrorCode.INVALID_PROGRAM,
                detail = "program is not semantically valid",
                validationErrors = validationErrors,
            )
        }

        val commandCount = countCommands(program.blocks)
        if (commandCount > Int.MAX_VALUE) {
            throw BlockProgramCompileException(
                code = BlockProgramCompileErrorCode.COMMAND_COUNT_OVERFLOW,
                detail = "compiled command count exceeds the supported output size",
            )
        }

        val commands = mutableListOf<HighLevelCommand>()
        appendCommands(program.blocks, commands)
        return HighLevelCommandProgram(
            protocolVersion = HighLevelCommandProtocolParser.SUPPORTED_PROTOCOL_VERSION,
            commands = commands,
        )
    }

    private fun countCommands(blocks: List<BlockInstance>): Long {
        var total = 0L
        blocks.forEach { block ->
            val blockCount = when (block.type) {
                BlockType.START, BlockType.END -> {
                    requireStructuralBlock(block)
                    0L
                }
                BlockType.REPEAT -> {
                    val count = repeatCount(block)
                    val childCount = countCommands(requireChildren(block))
                    try {
                        Math.multiplyExact(count, childCount)
                    } catch (_: ArithmeticException) {
                        throw BlockProgramCompileException(
                            code = BlockProgramCompileErrorCode.COMMAND_COUNT_OVERFLOW,
                            detail = "compiled command count exceeds the supported range",
                        )
                    }
                }

                else -> 1L
            }
            total = try {
                Math.addExact(total, blockCount)
            } catch (_: ArithmeticException) {
                throw BlockProgramCompileException(
                    code = BlockProgramCompileErrorCode.COMMAND_COUNT_OVERFLOW,
                    detail = "compiled command count exceeds the supported range",
                )
            }
        }
        return total
    }

    private fun appendCommands(blocks: List<BlockInstance>, commands: MutableList<HighLevelCommand>) {
        blocks.forEach { block ->
            when (block.type) {
                BlockType.START, BlockType.END -> Unit
                BlockType.REPEAT -> {
                    var remaining = repeatCount(block)
                    val children = requireChildren(block)
                    while (remaining > 0) {
                        appendCommands(children, commands)
                        remaining--
                    }
                }

                else -> commands += toCommand(block, commands.size)
            }
        }
    }

    private fun toCommand(block: BlockInstance, sequence: Int): HighLevelCommand = when (block.type) {
        BlockType.WAIT -> HighLevelCommand(
            sequence = sequence,
            sourceBlockId = block.id,
            type = CommandType.WAIT,
            parameters = expectParameters<BlockParameters.DurationSeconds>(block) {
                HighLevelCommandParameters.Wait(it.value)
            },
        )

        BlockType.MOVE_FORWARD, BlockType.MOVE_BACKWARD -> HighLevelCommand(
            sequence = sequence,
            sourceBlockId = block.id,
            type = CommandType.MOVE,
            parameters = expectParameters<BlockParameters.DistanceMeters>(block) {
                HighLevelCommandParameters.Move(
                    direction = if (block.type == BlockType.MOVE_FORWARD) MoveDirection.FORWARD else MoveDirection.BACKWARD,
                    distanceMeters = it.value,
                )
            },
        )

        BlockType.TURN_LEFT, BlockType.TURN_RIGHT -> HighLevelCommand(
            sequence = sequence,
            sourceBlockId = block.id,
            type = CommandType.TURN,
            parameters = expectParameters<BlockParameters.AngleDegrees>(block) {
                HighLevelCommandParameters.Turn(
                    direction = if (block.type == BlockType.TURN_LEFT) TurnDirection.LEFT else TurnDirection.RIGHT,
                    angleDegrees = it.value,
                )
            },
        )

        BlockType.STOP -> HighLevelCommand(
            sequence = sequence,
            sourceBlockId = block.id,
            type = CommandType.STOP,
            parameters = expectNone(block),
        )

        BlockType.SIT, BlockType.STAND -> HighLevelCommand(
            sequence = sequence,
            sourceBlockId = block.id,
            type = CommandType.POSTURE,
            parameters = expectNone(block) {
                HighLevelCommandParameters.PostureCommand(if (block.type == BlockType.SIT) Posture.SIT else Posture.STAND)
            },
        )

        BlockType.PRESET -> HighLevelCommand(
            sequence = sequence,
            sourceBlockId = block.id,
            type = CommandType.PRESET,
            parameters = expectParameters<BlockParameters.PresetCode>(block) {
                HighLevelCommandParameters.Preset(it.value)
            },
        )

        BlockType.START, BlockType.REPEAT, BlockType.END -> throw unexpectedParameters(block)
    }

    private inline fun <reified T : BlockParameters> expectParameters(
        block: BlockInstance,
        transform: (T) -> HighLevelCommandParameters,
    ): HighLevelCommandParameters {
        val parameters = block.parameters
        if (parameters !is T || block.children != null) throw unexpectedParameters(block)
        return transform(parameters)
    }

    private fun expectNone(block: BlockInstance): HighLevelCommandParameters {
        if (block.parameters != BlockParameters.None || block.children != null) throw unexpectedParameters(block)
        return HighLevelCommandParameters.None
    }

    private fun requireStructuralBlock(block: BlockInstance) {
        if (block.parameters != BlockParameters.None || block.children != null) throw unexpectedParameters(block)
    }

    private fun expectNone(block: BlockInstance, transform: () -> HighLevelCommandParameters): HighLevelCommandParameters {
        if (block.parameters != BlockParameters.None || block.children != null) throw unexpectedParameters(block)
        return transform()
    }

    private fun repeatCount(block: BlockInstance): Long {
        val parameters = block.parameters
        if (parameters !is BlockParameters.Count || parameters.value < 0) {
            throw BlockProgramCompileException(
                code = BlockProgramCompileErrorCode.INVALID_REPEAT_COUNT,
                detail = "REPEAT count must be non-negative",
            )
        }
        return parameters.value
    }

    private fun requireChildren(block: BlockInstance): List<BlockInstance> =
        block.children ?: throw BlockProgramCompileException(
            code = BlockProgramCompileErrorCode.INVALID_PROGRAM,
            detail = "REPEAT children are required",
        )

    private fun unexpectedParameters(block: BlockInstance): Nothing =
        throw BlockProgramCompileException(
            code = BlockProgramCompileErrorCode.INVALID_PROGRAM,
            detail = "${block.type.name} parameters do not match the Block Program model",
        )

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

enum class BlockProgramCompileErrorCode {
    INVALID_PROGRAM,
    UNSUPPORTED_SCHEMA_VERSION,
    INVALID_REPEAT_COUNT,
    COMMAND_COUNT_OVERFLOW,
}

class BlockProgramCompileException(
    val code: BlockProgramCompileErrorCode,
    detail: String,
    val validationErrors: List<BlockProgramValidationError> = emptyList(),
) : RuntimeException(detail)
