package team.inreok.poppyserver.domain.block.application

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType

@Component
class BlockProgramParser(
    private val objectMapper: ObjectMapper,
) {
    fun parse(document: String): BlockProgram {
        val root = try {
            objectMapper.readTree(document)
        } catch (exception: Exception) {
            throw BlockProgramParseException(BlockProgramParseErrorCode.MALFORMED_JSON, "JSON cannot be parsed", exception)
        }
        if (root == null || !root.isObject) {
            fail(BlockProgramParseErrorCode.MALFORMED_JSON, "program root must be an object")
        }
        requireFields(root, setOf("schemaVersion", "blocks"), "program")
        val schemaVersion = root.get("schemaVersion")
        if (!schemaVersion.isIntegralNumber || schemaVersion.toString().toIntOrNull() == null) {
            fail(BlockProgramParseErrorCode.INVALID_FIELD, "schemaVersion must be an integer")
        }
        val version = schemaVersion.toString().toInt()
        if (version !in SUPPORTED_SCHEMA_VERSIONS) {
            fail(BlockProgramParseErrorCode.UNSUPPORTED_SCHEMA_VERSION, "schemaVersion $version is not supported")
        }
        val blocksNode = root.get("blocks")
        if (!blocksNode.isArray) {
            fail(BlockProgramParseErrorCode.INVALID_FIELD, "blocks must be an array")
        }
        val blocks = blocksNode.values().map { parseBlock(it, version) }
        return BlockProgram(schemaVersion = version, blocks = blocks)
    }

    private fun parseBlock(node: JsonNode, schemaVersion: Int): BlockInstance {
        if (!node.isObject) {
            fail(BlockProgramParseErrorCode.INVALID_FIELD, "block must be an object")
        }
        requirePresentFields(node, setOf("id", "type", "parameters"), "block")
        val typeNode = node.get("type")
        val type = parseType(typeNode)
        requireTypeForSchema(type, schemaVersion)
        val allowedFields = if (type == BlockType.REPEAT) {
            setOf("id", "type", "parameters", "children")
        } else {
            setOf("id", "type", "parameters")
        }
        requireFields(node, allowedFields, "${type.name} block")
        val idNode = node.get("id")
        if (!idNode.isTextual || idNode.textValue().isBlank()) {
            fail(BlockProgramParseErrorCode.INVALID_FIELD, "block id must be a non-blank string")
        }
        val parameters = parseParameters(type, node.get("parameters"))
        val children = if (type == BlockType.REPEAT) {
            val childrenNode = node.get("children")
            if (!childrenNode.isArray) {
                fail(BlockProgramParseErrorCode.INVALID_FIELD, "REPEAT children must be an array")
            }
            childrenNode.values().map { parseBlock(it, schemaVersion) }
        } else {
            null
        }
        return BlockInstance(
            id = idNode.textValue(),
            type = type,
            parameters = parameters,
            children = children,
        )
    }

    private fun parseType(node: JsonNode): BlockType {
        if (!node.isTextual) {
            fail(BlockProgramParseErrorCode.INVALID_FIELD, "type must be a string")
        }
        return try {
            BlockType.valueOf(node.textValue())
        } catch (exception: IllegalArgumentException) {
            throw BlockProgramParseException(
                BlockProgramParseErrorCode.UNKNOWN_BLOCK_TYPE,
                "unknown block type ${node.textValue()}",
                exception,
            )
        }
    }

    private fun requireTypeForSchema(type: BlockType, schemaVersion: Int) {
        val legacyMotion = setOf(
            BlockType.MOVE_FORWARD,
            BlockType.MOVE_BACKWARD,
            BlockType.TURN_LEFT,
            BlockType.TURN_RIGHT,
        )
        val velocityMotion = setOf(BlockType.MOVE_VELOCITY, BlockType.TURN_VELOCITY)
        if ((schemaVersion == 1 && type in velocityMotion) || (schemaVersion == 2 && type in legacyMotion)) {
            fail(
                BlockProgramParseErrorCode.INVALID_FIELD,
                "${type.name} is not valid for schemaVersion $schemaVersion",
            )
        }
    }

    private fun parseParameters(type: BlockType, node: JsonNode): BlockParameters {
        if (!node.isObject) {
            fail(BlockProgramParseErrorCode.INVALID_FIELD, "parameters must be an object")
        }
        if (type == BlockType.MOVE_VELOCITY) {
            requireVelocityFields(node, "linearVelocityMetersPerSecond")
            return BlockParameters.VelocityMove(
                parseNonZeroNumber(node.get("linearVelocityMetersPerSecond"), "linearVelocityMetersPerSecond"),
                parsePositiveNumber(node.get("durationSeconds"), "durationSeconds"),
            )
        }
        if (type == BlockType.TURN_VELOCITY) {
            requireVelocityFields(node, "angularVelocityDegreesPerSecond")
            return BlockParameters.VelocityTurn(
                parseNonZeroNumber(node.get("angularVelocityDegreesPerSecond"), "angularVelocityDegreesPerSecond"),
                parsePositiveNumber(node.get("durationSeconds"), "durationSeconds"),
            )
        }
        val parameterName = type.parameterName
        val expectedFields = parameterName?.let(::setOf) ?: emptySet()
        val actualFields = node.properties().map { it.key }.toSet()
        val missingFields = expectedFields - actualFields
        val unknownFields = actualFields - expectedFields
        if (missingFields.isNotEmpty()) {
            fail(BlockProgramParseErrorCode.INVALID_PARAMETER, "${type.name} parameter is missing: $missingFields")
        }
        if (unknownFields.isNotEmpty()) {
            fail(BlockProgramParseErrorCode.INVALID_FIELD, "${type.name} parameters contain unknown fields: $unknownFields")
        }
        if (parameterName == null) {
            return BlockParameters.None
        }
        val parameter = node.get(parameterName)
        return when (type) {
            BlockType.WAIT -> BlockParameters.DurationSeconds(parseNumber(parameter, parameterName))
            BlockType.REPEAT -> BlockParameters.Count(parseInteger(parameter, parameterName))
            BlockType.MOVE_FORWARD, BlockType.MOVE_BACKWARD ->
                BlockParameters.DistanceMeters(parseNumber(parameter, parameterName))
            BlockType.TURN_LEFT, BlockType.TURN_RIGHT ->
                BlockParameters.AngleDegrees(parseNumber(parameter, parameterName))
            BlockType.PRESET -> BlockParameters.PresetCode(parseEnum(parameter, parameterName))
            BlockType.MOVE_VELOCITY, BlockType.TURN_VELOCITY -> BlockParameters.None
            else -> BlockParameters.None
        }
    }

    private fun parseNumber(node: JsonNode, name: String): Double {
        if (!node.isNumber) {
            fail(BlockProgramParseErrorCode.INVALID_PARAMETER, "$name must be a JSON number")
        }
        val value = node.toString().toDoubleOrNull()
        if (value == null || !value.isFinite()) {
            fail(BlockProgramParseErrorCode.INVALID_PARAMETER, "$name must be a finite JSON number")
        }
        return value
    }

    private fun parseNonZeroNumber(node: JsonNode, name: String): Double {
        val value = parseNumber(node, name)
        if (value == 0.0) fail(BlockProgramParseErrorCode.INVALID_PARAMETER, "$name must not be zero")
        return value
    }

    private fun parsePositiveNumber(node: JsonNode, name: String): Double {
        val value = parseNumber(node, name)
        if (value <= 0.0) fail(BlockProgramParseErrorCode.INVALID_PARAMETER, "$name must be positive")
        return value
    }

    private fun requireVelocityFields(node: JsonNode, velocityName: String) {
        val expected = setOf(velocityName, "durationSeconds")
        val actual = node.properties().map { it.key }.toSet()
        if (actual != expected) fail(BlockProgramParseErrorCode.INVALID_FIELD, "velocity parameters fields are invalid")
    }

    private fun parseInteger(node: JsonNode, name: String): Long {
        if (!node.isIntegralNumber) {
            fail(BlockProgramParseErrorCode.INVALID_PARAMETER, "$name must be an integer JSON number")
        }
        return node.toString().toLongOrNull()
            ?: fail(BlockProgramParseErrorCode.INVALID_PARAMETER, "$name is outside the supported integer range")
    }

    private fun parseEnum(node: JsonNode, name: String): String {
        if (!node.isTextual) {
            fail(BlockProgramParseErrorCode.INVALID_PARAMETER, "$name must be a string")
        }
        return node.textValue()
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
            fail(BlockProgramParseErrorCode.INVALID_FIELD, "$subject fields are invalid: $detail")
        }
    }

    private fun requirePresentFields(node: JsonNode, expected: Set<String>, subject: String) {
        val actual = node.properties().map { it.key }.toSet()
        val missing = expected - actual
        if (missing.isNotEmpty()) {
            fail(BlockProgramParseErrorCode.INVALID_FIELD, "$subject is missing fields: $missing")
        }
    }

    private fun fail(code: BlockProgramParseErrorCode, detail: String): Nothing =
        throw BlockProgramParseException(code, detail)

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        val SUPPORTED_SCHEMA_VERSIONS = setOf(1, 2)
    }
}
