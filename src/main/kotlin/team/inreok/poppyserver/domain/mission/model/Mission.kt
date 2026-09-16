package team.inreok.poppyserver.domain.mission.model

import java.util.UUID
import team.inreok.poppyserver.domain.block.model.BlockType

data class Mission(
    val id: UUID,
    val title: String,
    val difficulty: MissionDifficulty,
    val summary: String,
    val description: String,
    val goal: String,
    val completionCondition: CompletionCondition,
    val timeLimitSeconds: Long?,
    val estimatedSeconds: Long?,
    val allowedBlocks: List<BlockType>,
)
