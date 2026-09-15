package team.inreok.poppyserver.domain.block.model

data class BlockInstance(
    val id: String,
    val type: BlockType,
    val parameters: BlockParameters,
    val children: List<BlockInstance>? = null,
)
