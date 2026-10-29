package team.inreok.poppyserver.domain.block.application

object BlockProgramLimits {
    const val MIN_DISTANCE_METERS = 0.1
    const val MAX_DISTANCE_METERS = 3.0
    const val MIN_ANGLE_DEGREES = 1.0
    const val MAX_ANGLE_DEGREES = 360.0
    const val MIN_WAIT_SECONDS = 1.0
    const val MAX_WAIT_SECONDS = 10.0
    const val MIN_REPEAT_COUNT = 1L
    const val MAX_REPEAT_COUNT = 10L

    const val MAX_TOTAL_DISTANCE_METERS = 4.0
    const val MAX_TOTAL_SECONDS = 120.0

    const val LINEAR_SPEED_METERS_PER_SECOND = 0.5
    const val TURN_RATE_DEGREES_PER_SECOND = 45.0
    const val POSTURE_SETTLE_SECONDS = 3.0
    const val PRESET_SETTLE_SECONDS = 5.0

    val PRESET_OPTIONS: Map<String, String> = linkedMapOf(
        "HELLO" to "인사하기",
        "HEART" to "하트",
        "DANCE1" to "춤추기",
        "STRETCH" to "스트레칭",
    )
}
