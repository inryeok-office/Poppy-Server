package team.inreok.poppyserver.domain.arena.model

import kotlin.math.floor
import kotlin.math.min

/** 출발 시 로봇이 바라보는 방향. 값은 Agent 의 POPPY_PHYSICAL_START_HEADING 과 같다. */
enum class StartHeading(val code: String) {
    PLUS_Y("+Y"),
    PLUS_X("+X"),
    MINUS_Y("-Y"),
    MINUS_X("-X"),
    ;

    companion object {
        fun fromCode(code: String): StartHeading? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 체험 공간. 좌표는 m, 원점은 왼쪽 아래 모서리.
 * 이 값이 공간의 단일 출처이며 Client·Agent 는 조회해서 쓴다.
 */
data class ArenaSettings(
    val presetCode: String?,
    val widthM: Double,
    val depthM: Double,
    val marginM: Double,
    val startXM: Double,
    val startYM: Double,
    val startHeading: StartHeading,
) {
    /** 로봇이 들어갈 수 있는 영역(벽 여유 안쪽). */
    val usableWidthM: Double get() = round2(widthM - 2 * marginM)
    val usableDepthM: Double get() = round2(depthM - 2 * marginM)

    /**
     * 1회 이동 최대 거리. 한 번 이동한 뒤에도 제자리 회전 지름(0.8 m)이 영역 안에 남아야 하므로
     * `(가용 영역의 짧은 변) - 0.8` 을 0.1 m 단위로 내림하고 하드 상한 3 m 로 자른다.
     * 6×6(가용 5.2) → 3.0, 3×3(가용 2.0) → 1.2.
     */
    val maxMovePerCommandM: Double
        get() {
            val raw = min(usableWidthM, usableDepthM) - ArenaBounds.TURN_CIRCLE_DIAMETER_M
            return min(ArenaBounds.MAX_MOVE_PER_COMMAND_M, floorTenth(raw))
        }

    /** 새 이동 블록의 기본 거리. 최대 이동의 절반을 0.5 m 단위로 내림하고 0.5~1.0 m 로 자른다(6×6 → 1.0, 3×3 → 0.5). */
    val defaultMoveDistanceM: Double
        get() {
            val half = floor(maxMovePerCommandM / 2 / 0.5 + EPS) * 0.5
            return half.coerceIn(0.5, 1.0)
        }

    /** 모든 위반을 돌려준다(필드명 → 이유). 비어 있으면 유효. */
    fun violations(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        fun side(field: String, value: Double) {
            if (!value.isFinite() || value < ArenaBounds.MIN_SIDE_M - EPS || value > ArenaBounds.MAX_SIDE_M + EPS) {
                out[field] = "must be between ${ArenaBounds.MIN_SIDE_M} and ${ArenaBounds.MAX_SIDE_M} m"
            }
        }
        side("widthM", widthM)
        side("depthM", depthM)
        if (!marginM.isFinite() || marginM < ArenaBounds.MIN_MARGIN_M - EPS || marginM > ArenaBounds.MAX_MARGIN_M + EPS) {
            out["marginM"] = "must be between ${ArenaBounds.MIN_MARGIN_M} and ${ArenaBounds.MAX_MARGIN_M} m"
        }
        if (out.isEmpty()) {
            if (usableWidthM < ArenaBounds.MIN_USABLE_SIDE_M - EPS) {
                out["widthM"] = "usable width (width - 2*margin) must be at least ${ArenaBounds.MIN_USABLE_SIDE_M} m"
            }
            if (usableDepthM < ArenaBounds.MIN_USABLE_SIDE_M - EPS) {
                out["depthM"] = "usable depth (depth - 2*margin) must be at least ${ArenaBounds.MIN_USABLE_SIDE_M} m"
            }
        }
        if (out.isEmpty()) {
            if (!startXM.isFinite() || startXM < marginM - EPS || startXM > widthM - marginM + EPS) {
                out["startXM"] = "must be inside the margin (${marginM}..${round2(widthM - marginM)})"
            }
            if (!startYM.isFinite() || startYM < marginM - EPS || startYM > depthM - marginM + EPS) {
                out["startYM"] = "must be inside the margin (${marginM}..${round2(depthM - marginM)})"
            }
        }
        return out
    }

    private fun floorTenth(value: Double): Double = floor(value * 10 + EPS) / 10

    private fun round2(value: Double): Double = Math.round(value * 100) / 100.0

    companion object {
        private const val EPS = 1e-9
    }
}

/** 값 검증의 하한·상한. Agent 의 하드 상한(한 변 6 m, 벽 여유 0.4 m 이상, 1회 이동 3 m)보다 넓지 않다. */
object ArenaBounds {
    const val MIN_SIDE_M = 2.0
    const val MAX_SIDE_M = 6.0
    const val MIN_MARGIN_M = 0.4
    const val MAX_MARGIN_M = 1.0
    const val MIN_USABLE_SIDE_M = 1.3
    const val TURN_CIRCLE_DIAMETER_M = 0.8
    const val MAX_MOVE_PER_COMMAND_M = 3.0
}

object ArenaPresets {
    const val EXPO_6X6 = "6x6-박람회"
    const val BOOTH_3X3 = "3x3-부스"

    val ALL: Map<String, ArenaSettings> = linkedMapOf(
        EXPO_6X6 to ArenaSettings(EXPO_6X6, 6.0, 6.0, 0.4, 5.6, 0.4, StartHeading.PLUS_Y),
        BOOTH_3X3 to ArenaSettings(BOOTH_3X3, 3.0, 3.0, 0.5, 2.5, 0.5, StartHeading.PLUS_Y),
    )

    /** 설정이 없을 때의 값 = 지금까지의 동작(6×6). */
    val DEFAULT: ArenaSettings = ALL.getValue(EXPO_6X6)
}
