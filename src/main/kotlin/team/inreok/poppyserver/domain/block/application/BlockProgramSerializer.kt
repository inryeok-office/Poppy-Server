package team.inreok.poppyserver.domain.block.application

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType

class BlockProgramSerializer(
    private val objectMapper: ObjectMapper,
) {
    fun serialize(program: BlockProgram): String {
        validateProgram(program)
        val root = objectMapper.createObjectNode()
        root.put("schemaVersion", program.schemaVersion)
        val blocks = root.putArray("blocks")
        program.blocks.forEach { blocks.add(writeBlock(it)) }
        return objectMapper.writeValueAsString(root)
    }

    private fun validateProgram(program: BlockProgram) {
        if (program.schemaVersion != BlockProgramParser.SUPPORTED_SCHEMA_VERSION) {
            throw BlockProgramParseException(
                BlockProgramParseErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                "schemaVersion ${program.schemaVersion} is not supported",
            )
        }
        program.blocks.forEach(::validateBlock)
    }

    private fun validateBlock(block: BlockInstance) {
        if (block.id.isBlank()) {
            throw BlockProgramParseException(
                BlockProgramParseErrorCode.INVALID_FIELD,
                "block id must be a non-blank string",
            )
        }
        validateParameters(block.type, block.parameters)
        if (block.type == BlockType.REPEAT) {
            val children = block.children
                ?: throw BlockProgramParseException(
                    BlockProgramParseErrorCode.INVALID_FIELD,
                    "REPEAT children must be present",
                )
            children.forEach(::validateBlock)
        } else if (block.children != null) {
            throw BlockProgramParseException(
                BlockProgramParseErrorCode.INVALID_FIELD,
                "non-REPEAT block cannot have children",
            )
        }
    }

    private fun validateParameters(type: BlockType, parameters: BlockParameters) {
        val valid = when (type) {
            BlockType.WAIT -> parameters is BlockParameters.DurationSeconds && parameters.value.isFinite()
            BlockType.REPEAT -> parameters is BlockParameters.Count
            BlockType.MOVE_FORWARD, BlockType.MOVE_BACKWARD ->
                parameters is BlockParameters.DistanceMeters && parameters.value.isFinite()
            BlockType.TURN_LEFT, BlockType.TURN_RIGHT ->
                parameters is BlockParameters.AngleDegrees && parameters.value.isFinite()
            BlockType.PRESET -> parameters is BlockParameters.PresetCode
            BlockType.START, BlockType.END, BlockType.STOP, BlockType.SIT, BlockType.STAND ->
                parameters == BlockParameters.None
        }
        if (!valid) {
            throw BlockProgramParseException(
                BlockProgramParseErrorCode.INVALID_PARAMETER,
                "${type.name} parameters do not match the block type",
            )
        }
    }

    private fun writeBlock(block: BlockInstance): ObjectNode = objectMapper.createObjectNode().apply {
        put("id", block.id)
        put("type", block.type.name)
        val parametersNode = putObject("parameters")
        writeParameters(block.type, block.parameters, parametersNode)
        if (block.type == BlockType.REPEAT) {
            val childrenNode: tools.jackson.databind.node.ArrayNode = putArray("children")
            block.children.orEmpty().forEach { childrenNode.add(writeBlock(it)) }
        } else if (block.children != null) {
            throw IllegalArgumentException("non-REPEAT block cannot have children")
        }
    }

    private fun writeParameters(
        type: BlockType,
        parameters: BlockParameters,
        node: ObjectNode,
    ) {
        when (type) {
            BlockType.WAIT -> node.put("durationSeconds", (parameters as BlockParameters.DurationSeconds).value)
            BlockType.REPEAT -> node.put("count", (parameters as BlockParameters.Count).value)
            BlockType.MOVE_FORWARD, BlockType.MOVE_BACKWARD ->
                node.put("distanceMeters", (parameters as BlockParameters.DistanceMeters).value)
            BlockType.TURN_LEFT, BlockType.TURN_RIGHT ->
                node.put("angleDegrees", (parameters as BlockParameters.AngleDegrees).value)
            BlockType.PRESET -> node.put("presetCode", (parameters as BlockParameters.PresetCode).value)
            BlockType.START, BlockType.END, BlockType.STOP, BlockType.SIT, BlockType.STAND ->
                require(parameters == BlockParameters.None) { "${type.name} must not have parameters" }
        }
    }
}
