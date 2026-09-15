package team.inreok.poppyserver.domain.block.model

data class BlockDefinition(
    val type: BlockType,
    val displayName: String,
    val category: BlockCategory,
    val parameters: List<ParameterDefinition>,
    val available: Boolean,
    val freeModeAllowed: Boolean,
)
