package team.inreok.poppyserver.domain.session.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType
import team.inreok.poppyserver.domain.mission.application.MissionCatalog
import team.inreok.poppyserver.domain.session.model.ExperienceMode
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.global.response.FieldErrorItem

@Component
class MissionBlockGuard(
    private val missionCatalog: MissionCatalog,
) {
    fun ensureAllowed(session: Session, program: BlockProgram) {
        val missionId = session.missionId
        if (session.mode != ExperienceMode.MISSION || missionId == null) {
            return
        }
        val allowedTypes = missionCatalog.getById(missionId).allowedBlocks.toSet() + STRUCTURAL_TYPES
        val violations = program.blocks
            .flatMapIndexed { index, block -> collect(block, "blocks[$index]") }
            .filter { (block, _) -> block.type !in allowedTypes }
        if (violations.isNotEmpty()) {
            throw ApplicationException(
                errorCode = ErrorCode.BLOCK_PROGRAM_INVALID,
                fieldErrors = violations.map { (block, path) ->
                    FieldErrorItem(
                        field = path,
                        reason = "$BLOCK_NOT_ALLOWED_IN_MISSION: ${block.type.name} is not allowed in the Mission",
                    )
                },
            )
        }
    }

    private fun collect(block: BlockInstance, path: String): List<Pair<BlockInstance, String>> =
        listOf(block to path) + block.children.orEmpty().flatMapIndexed { index, child ->
            collect(child, "$path.children[$index]")
        }

    companion object {
        const val BLOCK_NOT_ALLOWED_IN_MISSION = "BLOCK_NOT_ALLOWED_IN_MISSION"
        private val STRUCTURAL_TYPES = setOf(BlockType.START, BlockType.END)
    }
}
