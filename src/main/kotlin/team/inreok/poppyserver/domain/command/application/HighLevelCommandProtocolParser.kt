package team.inreok.poppyserver.domain.command.application

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import team.inreok.poppyserver.domain.command.model.CommandType
import team.inreok.poppyserver.domain.command.model.HighLevelCommand
import team.inreok.poppyserver.domain.command.model.HighLevelCommandParameters
import team.inreok.poppyserver.domain.command.model.HighLevelCommandProgram
import team.inreok.poppyserver.domain.command.model.MoveDirection
import team.inreok.poppyserver.domain.command.model.Posture
import team.inreok.poppyserver.domain.command.model.TurnDirection

@Component
class HighLevelCommandProtocolParser(
    private val objectMapper: ObjectMapper,
) {
    fun parse(document: String): HighLevelCommandProgram {
        val root = try {
            objectMapper.readTree(document)
        } catch (exception: Exception) {
            throw HighLevelCommandProtocolException(
                HighLevelCommandProtocolErrorCode.MALFORMED_JSON,
                "JSON cannot be parsed",
                exception,
            )
        }
        if (root == null || !root.isObject) fail(HighLevelCommandProtocolErrorCode.MALFORMED_JSON, "program root must be an object")
        requireFields(root, setOf("protocolVersion", "commands"), "program")

        val versionNode = root.get("protocolVersion")
        if (!versionNode.isIntegralNumber || versionNode.toString().toIntOrNull() == null) {
            fail(HighLevelCommandProtocolErrorCode.INVALID_FIELD, "protocolVersion must be an integer")
        }
        val version = versionNode.toString().toInt()
        if (version != SUPPORTED_PROTOCOL_VERSION) {
            fail(HighLevelCommandProtocolErrorCode.UNSUPPORTED_PROTOCOL_VERSION, "protocolVersion $version is not supported")
        }

        val commandsNode = root.get("commands")
        if (!commandsNode.isArray) fail(HighLevelCommandProtocolErrorCode.INVALID_FIELD, "commands must be an array")
        val commands = commandsNode.values().mapIndexed(::parseCommand)
        return HighLevelCommandProgram(protocolVersion = version, commands = commands)
    }

    private fun parseCommand(index: Int, node: JsonNode): HighLevelCommand {
        if (!node.isObject) fail(HighLevelCommandProtocolErrorCode.INVALID_FIELD, "command must be an object")
        requireFields(node, setOf("sequence", "sourceBlockId", "type", "parameters"), "command")

        val sequenceNode = node.get("sequence")
        if (!sequenceNode.isIntegralNumber || sequenceNode.toString().toIntOrNull() == null) {
            fail(HighLevelCommandProtocolErrorCode.INVALID_SEQUENCE, "sequence must be an integer")
        }
        val sequence = sequenceNode.toString().toInt()
        if (sequence != index) {
            fail(HighLevelCommandProtocolErrorCode.INVALID_SEQUENCE, "sequence must be contiguous from zero")
        }

        val sourceBlockIdNode = node.get("sourceBlockId")
        if (!sourceBlockIdNode.isTextual || sourceBlockIdNode.textValue().isBlank()) {
            fail(HighLevelCommandProtocolErrorCode.INVALID_FIELD, "sourceBlockId must be a non-blank string")
        }

        val typeNode = node.get("type")
        if (!typeNode.isTextual) fail(HighLevelCommandProtocolErrorCode.INVALID_FIELD, "type must be a string")
        val type = try {
            CommandType.valueOf(typeNode.textValue())
        } catch (exception: IllegalArgumentException) {
            throw HighLevelCommandProtocolException(
                HighLevelCommandProtocolErrorCode.UNKNOWN_COMMAND_TYPE,
                "unknown command type ${typeNode.textValue()}",
                exception,
            )
        }

        return HighLevelCommand(
            sequence = sequence,
            sourceBlockId = sourceBlockIdNode.textValue(),
            type = type,
            parameters = parseParameters(type, node.get("parameters")),
        )
    }

    private fun parseParameters(type: CommandType, node: JsonNode): HighLevelCommandParameters {
        if (!node.isObject) fail(HighLevelCommandProtocolErrorCode.INVALID_PARAMETER, "parameters must be an object")
        return when (type) {
            CommandType.WAIT -> {
                requireParameterFields(node, setOf("durationSeconds"), type)
                HighLevelCommandParameters.Wait(parseNumber(node.get("durationSeconds"), "durationSeconds"))
            }

            CommandType.MOVE -> {
                requireParameterFields(node, setOf("direction", "distanceMeters"), type)
                HighLevelCommandParameters.Move(
                    direction = parseMoveDirection(node.get("direction")),
                    distanceMeters = parseNumber(node.get("distanceMeters"), "distanceMeters"),
                )
            }

            CommandType.TURN -> {
                requireParameterFields(node, setOf("direction", "angleDegrees"), type)
                HighLevelCommandParameters.Turn(
                    direction = parseTurnDirection(node.get("direction")),
                    angleDegrees = parseNumber(node.get("angleDegrees"), "angleDegrees"),
                )
            }

            CommandType.STOP -> {
                requireParameterFields(node, emptySet(), type)
                HighLevelCommandParameters.None
            }

            CommandType.POSTURE -> {
                requireParameterFields(node, setOf("posture"), type)
                HighLevelCommandParameters.PostureCommand(parsePosture(node.get("posture")))
            }

            CommandType.PRESET -> {
                requireParameterFields(node, setOf("presetCode"), type)
                val presetCode = node.get("presetCode")
                if (!presetCode.isTextual || presetCode.textValue().isBlank()) {
                    fail(HighLevelCommandProtocolErrorCode.INVALID_PARAMETER, "presetCode must be a non-blank string")
                }
                HighLevelCommandParameters.Preset(presetCode.textValue())
            }
        }
    }

    private fun parseNumber(node: JsonNode, name: String): Double {
        if (!node.isNumber) fail(HighLevelCommandProtocolErrorCode.INVALID_PARAMETER, "$name must be a JSON number")
        val value = node.toString().toDoubleOrNull()
        if (value == null || !value.isFinite()) {
            fail(HighLevelCommandProtocolErrorCode.INVALID_PARAMETER, "$name must be a finite JSON number")
        }
        return value
    }

    private fun parseMoveDirection(node: JsonNode): MoveDirection {
        if (!node.isTextual) fail(HighLevelCommandProtocolErrorCode.INVALID_PARAMETER, "direction must be a string")
        return try {
            MoveDirection.valueOf(node.textValue())
        } catch (exception: IllegalArgumentException) {
            throw HighLevelCommandProtocolException(
                HighLevelCommandProtocolErrorCode.INVALID_PARAMETER,
                "direction is invalid for MOVE",
                exception,
            )
        }
    }

    private fun parseTurnDirection(node: JsonNode): TurnDirection {
        if (!node.isTextual) fail(HighLevelCommandProtocolErrorCode.INVALID_PARAMETER, "direction must be a string")
        return try {
            TurnDirection.valueOf(node.textValue())
        } catch (exception: IllegalArgumentException) {
            throw HighLevelCommandProtocolException(
                HighLevelCommandProtocolErrorCode.INVALID_PARAMETER,
                "direction is invalid for TURN",
                exception,
            )
        }
    }

    private fun parsePosture(node: JsonNode): Posture {
        if (!node.isTextual) fail(HighLevelCommandProtocolErrorCode.INVALID_PARAMETER, "posture must be a string")
        return try {
            Posture.valueOf(node.textValue())
        } catch (exception: IllegalArgumentException) {
            throw HighLevelCommandProtocolException(
                HighLevelCommandProtocolErrorCode.INVALID_PARAMETER,
                "posture is invalid",
                exception,
            )
        }
    }

    private fun requireParameterFields(node: JsonNode, expected: Set<String>, type: CommandType) {
        requireFields(node, expected, "${type.name} parameters")
    }

    private fun requireFields(node: JsonNode, expected: Set<String>, subject: String) {
        val actual = node.properties().map { it.key }.toSet()
        if (actual != expected) {
            val missing = expected - actual
            val unknown = actual - expected
            val detail = buildString {
                if (missing.isNotEmpty()) append("missing ${missing.sorted()}")
                if (unknown.isNotEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append("unknown ${unknown.sorted()}")
                }
            }
            fail(HighLevelCommandProtocolErrorCode.INVALID_FIELD, "$subject fields are invalid: $detail")
        }
    }

    private fun fail(code: HighLevelCommandProtocolErrorCode, detail: String): Nothing =
        throw HighLevelCommandProtocolException(code, detail)

    companion object {
        const val SUPPORTED_PROTOCOL_VERSION = 1
    }
}
