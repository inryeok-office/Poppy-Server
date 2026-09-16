package team.inreok.poppyserver.domain.robot.application

enum class ExecutionCapabilityCode(
    val code: String,
) {
    MOVE("COMMAND_MOVE"),
    TURN("COMMAND_TURN"),
    STOP("COMMAND_STOP"),
    POSTURE("COMMAND_POSTURE"),
    PRESET("COMMAND_PRESET"),
}
