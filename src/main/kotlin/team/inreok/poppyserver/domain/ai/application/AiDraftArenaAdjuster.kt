package team.inreok.poppyserver.domain.ai.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.arena.application.ArenaSettingsProvider
import team.inreok.poppyserver.domain.arena.application.DefaultArenaSettingsProvider
import team.inreok.poppyserver.domain.arena.model.ArenaPresets
import team.inreok.poppyserver.domain.arena.model.ArenaSettings
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * AI 초안의 이동 거리를 현재 체험 공간에 맞춘다(재학습 없이 후처리).
 *
 * 모델은 6×6 매트 데이터로 학습돼, 거리를 말하지 않으면 1~2 m 를 만든다. 공간이 기본(6×6)과 다를 때만
 * - 거리를 말하지 않은 문장: 모든 이동을 공간의 기본 이동 거리(3×3 → 0.5 m)로 바꾼다
 * - 거리를 말한 문장: 공간의 1회 최대 이동(3×3 → 1.2 m)을 넘는 값만 줄인다
 * 6×6(기본)에서는 아무것도 바꾸지 않는다 — 지금까지의 동작과 같다.
 */
@Component
class AiDraftArenaAdjuster(
    private val arenaSettingsProvider: ArenaSettingsProvider = DefaultArenaSettingsProvider,
) {
    fun adjust(utterance: String, steps: List<Map<String, Any>>): AdjustedDraft {
        val arena = arenaSettingsProvider.current()
        if (sameGeometry(arena, ArenaPresets.DEFAULT)) return AdjustedDraft(steps, note = null)

        val explicit = EXPLICIT_DISTANCE.containsMatchIn(utterance)
        var defaulted = false
        var clamped = false
        fun adjustAll(list: List<Map<String, Any>>): List<Map<String, Any>> = list.map { step ->
            when (step["kind"]) {
                "moveForward", "move" -> {
                    val current = (step["distanceM"] as Number).toDouble()
                    val next = if (explicit) {
                        min(current, arena.maxMovePerCommandM)
                    } else {
                        arena.defaultMoveDistanceM
                    }
                    if (next != current) {
                        if (explicit) clamped = true else defaulted = true
                    }
                    step + ("distanceM" to tenths(next))
                }
                "repeat" -> {
                    @Suppress("UNCHECKED_CAST")
                    val body = step["body"] as List<Map<String, Any>>
                    step + ("body" to adjustAll(body))
                }
                else -> step
            }
        }
        val adjusted = adjustAll(steps)
        val note = when {
            clamped -> "이 공간에서는 한 번에 ${meters(arena.maxMovePerCommandM)}m 까지 갈 수 있어서 줄였어요."
            defaulted -> "거리를 말하지 않아서 이 공간에 맞게 ${meters(arena.defaultMoveDistanceM)}m 씩 가도록 만들었어요."
            else -> null
        }
        return AdjustedDraft(adjusted, note)
    }

    private fun sameGeometry(a: ArenaSettings, b: ArenaSettings): Boolean =
        a.widthM == b.widthM && a.depthM == b.depthM && a.marginM == b.marginM &&
            a.startXM == b.startXM && a.startYM == b.startYM && a.startHeading == b.startHeading

    /** 검증기와 같은 숫자 모양: 정수면 Long, 아니면 0.1 단위 Double. */
    private fun tenths(value: Double): Number {
        val t = (value * 10).roundToLong()
        return if (t % 10 == 0L) t / 10 else t / 10.0
    }

    private fun meters(value: Double): String = tenths(value).toString()

    private companion object {
        /** "1미터", "1.5 m", "50cm", "반 미터", "두 미터" 처럼 거리를 직접 말했는지. */
        val EXPLICIT_DISTANCE = Regex(
            """(\d+(\.\d+)?|반|한|두|세|네|다섯|일|이|삼|사|오)\s*(미터|m(?![a-z])|센티|센치|cm)""",
            RegexOption.IGNORE_CASE,
        )
    }
}

data class AdjustedDraft(val steps: List<Map<String, Any>>, val note: String?)
