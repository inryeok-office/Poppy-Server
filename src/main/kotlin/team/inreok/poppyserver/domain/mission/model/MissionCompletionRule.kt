package team.inreok.poppyserver.domain.mission.model

import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType

data class MinimumExecutions(
    val types: Set<BlockType>,
    val count: Int,
)

data class MissionCompletionRule(
    val requiredBlocks: List<Set<BlockType>> = emptyList(),
    val forbiddenBlocks: Set<BlockType> = emptySet(),
    val requiredOrder: List<Set<BlockType>> = emptyList(),
    val minimumExecutions: List<MinimumExecutions> = emptyList(),
    val lastAction: BlockType? = null,
    val maxBlockCount: Int? = null,
) {
    fun evaluate(program: BlockProgram): MissionResult {
        val placed = program.blocks.flatMap(::flatten).filterNot { it.type in STRUCTURAL_TYPES }
        val executed = program.blocks.flatMap(::expand).filterNot { it in STRUCTURAL_TYPES }
        val reasons = buildList {
            requiredBlocks
                .filter { required -> placed.none { it.type in required } }
                .forEach { add(MissionFailureReason(MissionFailureCode.MISSING_REQUIRED_BLOCK, it.label())) }
            placed.map { it.type }.distinct().filter { it in forbiddenBlocks }
                .forEach { add(MissionFailureReason(MissionFailureCode.FORBIDDEN_BLOCK, it.name)) }
            if (!containsInOrder(executed, requiredOrder)) {
                add(MissionFailureReason(MissionFailureCode.ORDER_NOT_SATISFIED, requiredOrder.joinToString(" -> ") { it.label() }))
            }
            minimumExecutions
                .filter { minimum -> executed.count { it in minimum.types } < minimum.count }
                .forEach { add(MissionFailureReason(MissionFailureCode.TOO_FEW_EXECUTIONS, "${it.types.label()} x${it.count}")) }
            lastAction?.let { expected ->
                if (executed.lastOrNull { it != BlockType.WAIT } != expected) {
                    add(MissionFailureReason(MissionFailureCode.LAST_ACTION_MISMATCH, expected.name))
                }
            }
            maxBlockCount?.let { max ->
                if (placed.size > max) {
                    add(MissionFailureReason(MissionFailureCode.TOO_MANY_BLOCKS, "${placed.size} > $max"))
                }
            }
        }
        return MissionResult(passed = reasons.isEmpty(), reasons = reasons)
    }

    private fun flatten(block: BlockInstance): List<BlockInstance> =
        listOf(block) + block.children.orEmpty().flatMap(::flatten)

    private fun expand(block: BlockInstance): List<BlockType> {
        if (block.type != BlockType.REPEAT) {
            return listOf(block.type)
        }
        val count = (block.parameters as? BlockParameters.Count)?.value?.toInt() ?: 0
        val body = block.children.orEmpty().flatMap(::expand)
        return List(count) { body }.flatten()
    }

    private fun containsInOrder(executed: List<BlockType>, order: List<Set<BlockType>>): Boolean {
        var index = 0
        executed.forEach { type ->
            if (index < order.size && type in order[index]) {
                index++
            }
        }
        return index == order.size
    }

    private fun Set<BlockType>.label(): String = sortedBy { it.ordinal }.joinToString("|") { it.name }

    companion object {
        private val STRUCTURAL_TYPES = setOf(BlockType.START, BlockType.END)
    }
}

enum class MissionFailureCode {
    MISSING_REQUIRED_BLOCK,
    FORBIDDEN_BLOCK,
    ORDER_NOT_SATISFIED,
    TOO_FEW_EXECUTIONS,
    LAST_ACTION_MISMATCH,
    TOO_MANY_BLOCKS,
}

data class MissionFailureReason(
    val code: MissionFailureCode,
    val detail: String,
)

data class MissionResult(
    val passed: Boolean,
    val reasons: List<MissionFailureReason>,
)
