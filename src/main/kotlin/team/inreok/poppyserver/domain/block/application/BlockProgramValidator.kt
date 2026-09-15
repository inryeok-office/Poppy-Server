package team.inreok.poppyserver.domain.block.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType

@Component
class BlockProgramValidator {
    fun validate(program: BlockProgram): BlockProgramValidationResult {
        val errors = mutableListOf<BlockProgramValidationError>()
        val locations = program.blocks.flatMapIndexed { index, block ->
            collect(block, "blocks[$index]", insideRepeat = false)
        }

        if (program.blocks.isEmpty()) {
            errors += BlockProgramValidationError(
                code = BlockProgramValidationErrorCode.BLOCKS_REQUIRED,
                message = "program must contain at least one block",
            )
        }

        val startLocations = locations.filter { it.block.type == BlockType.START }
        when {
            startLocations.isEmpty() -> errors += BlockProgramValidationError(
                code = BlockProgramValidationErrorCode.START_REQUIRED,
                message = "program must contain one START block",
            )

            startLocations.size > 1 -> startLocations.drop(1).forEach { location ->
                errors += location.error(
                    code = BlockProgramValidationErrorCode.START_DUPLICATED,
                    message = "program must contain only one START block",
                )
            }
        }
        if (program.blocks.firstOrNull()?.type != BlockType.START) {
            errors += BlockProgramValidationError(
                code = BlockProgramValidationErrorCode.START_NOT_FIRST,
                path = "blocks[0]",
                message = "START block must be the first top-level block",
            )
        }

        val endLocations = locations.filter { it.block.type == BlockType.END }
        when {
            endLocations.isEmpty() -> errors += BlockProgramValidationError(
                code = BlockProgramValidationErrorCode.END_REQUIRED,
                message = "program must contain one END block",
            )

            endLocations.size > 1 -> endLocations.drop(1).forEach { location ->
                errors += location.error(
                    code = BlockProgramValidationErrorCode.END_DUPLICATED,
                    message = "program must contain only one END block",
                )
            }
        }
        if (program.blocks.lastOrNull()?.type != BlockType.END) {
            errors += BlockProgramValidationError(
                code = BlockProgramValidationErrorCode.END_NOT_LAST,
                path = "blocks[${program.blocks.lastIndex}]",
                message = "END block must be the last top-level block",
            )
        }

        locations.groupBy { it.block.id }.filterValues { it.size > 1 }.values.flatMap { it.drop(1) }.forEach { location ->
            errors += location.error(
                code = BlockProgramValidationErrorCode.DUPLICATE_BLOCK_ID,
                message = "block id must be unique within the program",
            )
        }

        locations.forEach { location ->
            if (location.insideRepeat && location.block.type in setOf(BlockType.START, BlockType.END)) {
                errors += location.error(
                    code = BlockProgramValidationErrorCode.STRUCTURAL_BLOCK_INSIDE_REPEAT,
                    message = "START and END cannot be inside REPEAT",
                )
            }
            if (location.insideRepeat && location.block.type == BlockType.REPEAT) {
                errors += location.error(
                    code = BlockProgramValidationErrorCode.NESTED_REPEAT_NOT_ALLOWED,
                    message = "REPEAT cannot contain another REPEAT",
                )
            }
            if (location.block.type == BlockType.REPEAT && location.block.children.isNullOrEmpty()) {
                errors += location.error(
                    code = BlockProgramValidationErrorCode.REPEAT_CHILDREN_EMPTY,
                    message = "REPEAT must contain at least one child block",
                )
            }
        }

        return BlockProgramValidationResult(errors)
    }

    private fun collect(block: BlockInstance, path: String, insideRepeat: Boolean): List<BlockLocation> {
        val location = BlockLocation(block, path, insideRepeat)
        if (block.type != BlockType.REPEAT || block.children == null) return listOf(location)
        return listOf(location) + block.children.flatMapIndexed { index, child ->
            collect(child, "$path.children[$index]", insideRepeat = true)
        }
    }

    private data class BlockLocation(
        val block: BlockInstance,
        val path: String,
        val insideRepeat: Boolean,
    ) {
        fun error(code: BlockProgramValidationErrorCode, message: String): BlockProgramValidationError =
            BlockProgramValidationError(code = code, blockId = block.id, path = path, message = message)
    }
}

enum class BlockProgramValidationErrorCode {
    BLOCKS_REQUIRED,
    START_REQUIRED,
    START_DUPLICATED,
    START_NOT_FIRST,
    END_REQUIRED,
    END_DUPLICATED,
    END_NOT_LAST,
    DUPLICATE_BLOCK_ID,
    REPEAT_CHILDREN_EMPTY,
    NESTED_REPEAT_NOT_ALLOWED,
    STRUCTURAL_BLOCK_INSIDE_REPEAT,
}

data class BlockProgramValidationError(
    val code: BlockProgramValidationErrorCode,
    val blockId: String? = null,
    val path: String? = null,
    val message: String,
)

data class BlockProgramValidationResult(
    val errors: List<BlockProgramValidationError>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}
