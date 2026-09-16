package team.inreok.poppyserver.domain.command.model

enum class CommandType {
    WAIT,
    MOVE,
    TURN,
    STOP,
    POSTURE,
    PRESET,
}

enum class MoveDirection {
    FORWARD,
    BACKWARD,
}

enum class TurnDirection {
    LEFT,
    RIGHT,
}

enum class Posture {
    SIT,
    STAND,
}

sealed interface HighLevelCommandParameters {
    data object None : HighLevelCommandParameters

    data class Wait(val durationSeconds: Double) : HighLevelCommandParameters

    data class Move(val direction: MoveDirection, val distanceMeters: Double) : HighLevelCommandParameters

    data class Turn(val direction: TurnDirection, val angleDegrees: Double) : HighLevelCommandParameters

    data class PostureCommand(val posture: Posture) : HighLevelCommandParameters

    data class Preset(val presetCode: String) : HighLevelCommandParameters
}

data class HighLevelCommand(
    val sequence: Int,
    val sourceBlockId: String,
    val type: CommandType,
    val parameters: HighLevelCommandParameters,
)

data class HighLevelCommandProgram(
    val protocolVersion: Int,
    val commands: List<HighLevelCommand>,
)
