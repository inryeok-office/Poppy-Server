package team.inreok.poppyserver.domain.block.model

enum class BlockType(
    val category: BlockCategory,
    val parameterName: String? = null,
    val parameterType: ParameterType? = null,
) {
    START(BlockCategory.FLOW),
    WAIT(BlockCategory.FLOW, "durationSeconds", ParameterType.NUMBER),
    REPEAT(BlockCategory.FLOW, "count", ParameterType.INTEGER),
    END(BlockCategory.FLOW),
    MOVE_FORWARD(BlockCategory.MOVEMENT, "distanceMeters", ParameterType.NUMBER),
    MOVE_BACKWARD(BlockCategory.MOVEMENT, "distanceMeters", ParameterType.NUMBER),
    TURN_LEFT(BlockCategory.MOVEMENT, "angleDegrees", ParameterType.NUMBER),
    TURN_RIGHT(BlockCategory.MOVEMENT, "angleDegrees", ParameterType.NUMBER),
    STOP(BlockCategory.MOVEMENT),
    SIT(BlockCategory.ACTION),
    STAND(BlockCategory.ACTION),
    PRESET(BlockCategory.ACTION, "presetCode", ParameterType.ENUM),
}
