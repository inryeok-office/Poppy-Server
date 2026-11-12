package team.inreok.poppyserver.domain.ai.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.arena.application.ArenaSettingsProvider
import team.inreok.poppyserver.domain.arena.application.DefaultArenaSettingsProvider
import tools.jackson.databind.JsonNode
import kotlin.math.abs
import kotlin.math.roundToLong

@Component
class AiDraftStepValidator(
    private val arenaSettingsProvider: ArenaSettingsProvider = DefaultArenaSettingsProvider,
) {
    fun validate(steps: JsonNode?): List<Map<String, Any>> {
        if (steps == null || !steps.isArray || steps.isEmpty) {
            throw InvalidAiDraftException("steps must be a non-empty array")
        }
        val validated = elements(steps).map { validateStep(it, insideRepeat = false) }
        if (structuralCount(validated) > MAX_BLOCKS) {
            throw InvalidAiDraftException("too many blocks")
        }
        return validated
    }

    private fun validateStep(node: JsonNode, insideRepeat: Boolean): Map<String, Any> {
        if (!node.isObject) throw InvalidAiDraftException("step must be an object")
        val kind = node.get("kind")?.takeIf { it.isString }?.asString()
            ?: throw InvalidAiDraftException("kind is required")
        return when (kind) {
            "moveForward", "move" -> mapOf("kind" to kind, "distanceM" to decimal(node.get("distanceM"), MIN_DISTANCE, arenaSettingsProvider.current().maxMovePerCommandM))
            "rotateLeft", "rotateRight" -> mapOf("kind" to kind, "degrees" to integer(node.get("degrees"), 1, 360))
            "wait" -> mapOf("kind" to kind, "seconds" to integer(node.get("seconds"), 1, 10))
            in SIMPLE_KINDS -> mapOf("kind" to kind)
            "repeat" -> validateRepeat(node, insideRepeat)
            else -> throw InvalidAiDraftException("kind is not allowed")
        }
    }

    private fun validateRepeat(node: JsonNode, insideRepeat: Boolean): Map<String, Any> {
        if (insideRepeat) throw InvalidAiDraftException("nested repeat is not allowed")
        val count = integer(node.get("count"), 1, 10)
        val body = node.get("body")
        if (body == null || !body.isArray || body.isEmpty) throw InvalidAiDraftException("repeat body is empty")
        return mapOf("kind" to "repeat", "count" to count, "body" to elements(body).map { validateStep(it, insideRepeat = true) })
    }

    private fun decimal(node: JsonNode?, min: Double, max: Double): Number {
        if (node == null || !node.isNumber) throw InvalidAiDraftException("number is required")
        val value = node.asDouble()
        val tenths = value * 10
        if (!value.isFinite() || value < min || value > max || abs(tenths - tenths.roundToLong()) > EPSILON) {
            throw InvalidAiDraftException("number is out of range")
        }
        val rounded = tenths.roundToLong()
        return if (rounded % 10 == 0L) rounded / 10 else rounded / 10.0
    }

    private fun integer(node: JsonNode?, min: Long, max: Long): Long {
        if (node == null || !node.isNumber) throw InvalidAiDraftException("number is required")
        val value = node.asDouble()
        if (!value.isFinite() || value != value.roundToLong().toDouble() || value < min || value > max) {
            throw InvalidAiDraftException("number is out of range")
        }
        return value.roundToLong()
    }

    private fun elements(array: JsonNode): List<JsonNode> = (0 until array.size()).map { array.get(it) }

    @Suppress("UNCHECKED_CAST")
    private fun structuralCount(steps: List<Map<String, Any>>): Int =
        steps.sumOf { 1 + ((it["body"] as? List<Map<String, Any>>)?.let(::structuralCount) ?: 0) }

    private companion object {
        const val MAX_BLOCKS = 12
        const val MIN_DISTANCE = 0.1
        const val EPSILON = 1e-6
        val SIMPLE_KINDS = setOf("stop", "standUp", "greet", "heart", "dance")
    }
}

class InvalidAiDraftException(message: String) : RuntimeException(message)
