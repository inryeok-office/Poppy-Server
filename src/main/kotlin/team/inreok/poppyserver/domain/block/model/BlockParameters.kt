package team.inreok.poppyserver.domain.block.model

sealed interface BlockParameters {
    data object None : BlockParameters

    data class DurationSeconds(val value: Double) : BlockParameters

    data class Count(val value: Long) : BlockParameters

    data class DistanceMeters(val value: Double) : BlockParameters

    data class AngleDegrees(val value: Double) : BlockParameters

    data class PresetCode(val value: String) : BlockParameters
}
