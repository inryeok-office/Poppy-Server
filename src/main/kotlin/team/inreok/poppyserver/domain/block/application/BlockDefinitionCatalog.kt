package team.inreok.poppyserver.domain.block.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.arena.application.ArenaSettingsProvider
import team.inreok.poppyserver.domain.arena.application.DefaultArenaSettingsProvider
import team.inreok.poppyserver.domain.block.model.BlockCategory
import team.inreok.poppyserver.domain.block.model.BlockDefinition
import team.inreok.poppyserver.domain.block.model.BlockType
import team.inreok.poppyserver.domain.block.model.ParameterDefinition

@Component
class BlockDefinitionCatalog(
    private val arenaSettingsProvider: ArenaSettingsProvider = DefaultArenaSettingsProvider,
) {
    /** 이동 블록의 최대 거리는 현재 체험 공간(1회 이동 한도)을 따른다. */
    fun list(): List<BlockDefinition> = definitions(arenaSettingsProvider.current().maxMovePerCommandM)

    companion object {
        private fun definitions(maxMoveMeters: Double): List<BlockDefinition> = listOf(
            definition(BlockType.START, "시작"),
            definition(
                BlockType.WAIT,
                "대기",
                unit = "s",
                min = BlockProgramLimits.MIN_WAIT_SECONDS,
                max = BlockProgramLimits.MAX_WAIT_SECONDS,
            ),
            definition(
                BlockType.REPEAT,
                "반복",
                unit = "count",
                min = BlockProgramLimits.MIN_REPEAT_COUNT.toDouble(),
                max = BlockProgramLimits.MAX_REPEAT_COUNT.toDouble(),
            ),
            definition(BlockType.END, "종료"),
            definition(
                BlockType.MOVE_FORWARD,
                "앞으로 이동",
                unit = "m",
                min = BlockProgramLimits.MIN_DISTANCE_METERS,
                max = maxMoveMeters,
            ),
            definition(
                BlockType.MOVE_BACKWARD,
                "뒤로 이동",
                unit = "m",
                min = BlockProgramLimits.MIN_DISTANCE_METERS,
                max = maxMoveMeters,
            ),
            definition(
                BlockType.TURN_LEFT,
                "왼쪽 회전",
                unit = "deg",
                min = BlockProgramLimits.MIN_ANGLE_DEGREES,
                max = BlockProgramLimits.MAX_ANGLE_DEGREES,
            ),
            definition(
                BlockType.TURN_RIGHT,
                "오른쪽 회전",
                unit = "deg",
                min = BlockProgramLimits.MIN_ANGLE_DEGREES,
                max = BlockProgramLimits.MAX_ANGLE_DEGREES,
            ),
            definition(BlockType.STOP, "정지"),
            definition(BlockType.SIT, "앉기"),
            definition(BlockType.STAND, "일어서기"),
            definition(BlockType.PRESET, "특수 동작", presetOptions = BlockProgramLimits.PRESET_OPTIONS),
        )

        private fun definition(
            type: BlockType,
            displayName: String,
            unit: String? = null,
            available: Boolean = true,
            min: Double? = null,
            max: Double? = null,
            presetOptions: Map<String, String> = emptyMap(),
        ): BlockDefinition {
            val parameter = type.parameterName?.let {
                ParameterDefinition(
                    name = it,
                    type = requireNotNull(type.parameterType),
                    unit = unit,
                    required = true,
                    min = min,
                    max = max,
                    options = presetOptions.keys.toList(),
                    optionLabels = presetOptions,
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
