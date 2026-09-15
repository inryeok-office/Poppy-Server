package team.inreok.poppyserver.domain.block.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.block.model.BlockCategory
import team.inreok.poppyserver.domain.block.model.BlockDefinition
import team.inreok.poppyserver.domain.block.model.BlockType
import team.inreok.poppyserver.domain.block.model.ParameterDefinition

@Component
class BlockDefinitionCatalog {
    fun list(): List<BlockDefinition> = DEFINITIONS

    companion object {
        private val DEFINITIONS = listOf(
            definition(BlockType.START, "시작"),
            definition(BlockType.WAIT, "대기", unit = "s"),
            definition(BlockType.REPEAT, "반복", unit = "count"),
            definition(BlockType.END, "종료"),
            definition(BlockType.MOVE_FORWARD, "앞으로 이동", unit = "m"),
            definition(BlockType.MOVE_BACKWARD, "뒤로 이동", unit = "m"),
            definition(BlockType.TURN_LEFT, "왼쪽 회전", unit = "deg"),
            definition(BlockType.TURN_RIGHT, "오른쪽 회전", unit = "deg"),
            definition(BlockType.STOP, "정지"),
            definition(BlockType.SIT, "앉기"),
            definition(BlockType.STAND, "일어서기"),
            definition(BlockType.PRESET, "특수 동작", available = false),
        )

        private fun definition(
            type: BlockType,
            displayName: String,
            unit: String? = null,
            available: Boolean = true,
        ): BlockDefinition {
            val parameter = type.parameterName?.let {
                ParameterDefinition(
                    name = it,
                    type = requireNotNull(type.parameterType),
                    unit = unit,
                    required = true,
                )
            }
            return BlockDefinition(
                type = type,
                displayName = displayName,
                category = type.category,
                parameters = listOfNotNull(parameter),
                available = available,
                freeModeAllowed = available,
            )
        }
    }
}
