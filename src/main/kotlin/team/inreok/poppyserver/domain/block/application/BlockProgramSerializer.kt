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
        val root = objectMapper.createObjectNode()
        root.put("schemaVersion", program.schemaVersion)
        val blocks = root.putArray("blocks")
        program.blocks.forEach { blocks.add(writeBlock(it)) }
        return objectMapper.writeValueAsString(root)
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
