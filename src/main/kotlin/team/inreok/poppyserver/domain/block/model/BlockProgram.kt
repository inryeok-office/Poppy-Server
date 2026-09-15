package team.inreok.poppyserver.domain.block.model

data class BlockProgram(
    val schemaVersion: Int,
    val blocks: List<BlockInstance>,
)
