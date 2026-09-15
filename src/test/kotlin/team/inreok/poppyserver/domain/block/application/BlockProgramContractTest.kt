package team.inreok.poppyserver.domain.block.application

import org.junit.jupiter.api.Test
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
import team.inreok.poppyserver.domain.block.model.BlockType
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlockProgramContractTest {
    private val objectMapper: ObjectMapper = JsonMapper.builder().build()
    private val parser = BlockProgramParser(objectMapper)
    private val serializer = BlockProgramSerializer(objectMapper)

    @Test
    fun `canonical fixture parses and round trips`() {
        val fixture = requireNotNull(javaClass.getResource("/contracts/block-program-v1.json")).readText()
        val parsed = parser.parse(fixture)

        assertEquals(1, parsed.schemaVersion)
        assertEquals(4, parsed.blocks.size)
        assertEquals(BlockType.REPEAT, parsed.blocks[2].type)
        assertEquals(2, parsed.blocks[2].children?.size)
        assertEquals(BlockParameters.DistanceMeters(1.0), parsed.blocks[1].parameters)
        assertEquals(BlockParameters.Count(2), parsed.blocks[2].parameters)

        assertEquals(objectMapper.readTree(fixture), objectMapper.readTree(serializer.serialize(parsed)))
    }

    @Test
    fun `all block types parse`() {
        BlockType.entries.forEach { type ->
            val block = blockJson("${type.name}-id", type.name, parametersFor(type))
            val blockWithChildren = if (type == BlockType.REPEAT) {
                block.dropLast(1) + ",\"children\":[${blockJson("child", "STOP", "{}")}] }"
            } else {
                block
            }
            val parsed = parser.parse(programJson(blockWithChildren))
            assertEquals(type, parsed.blocks.single().type)
        }
    }

    @Test
    fun `multiple instances of one type retain independent ids`() {
        val program = parser.parse(
            """
            {"schemaVersion":1,"blocks":[
              ${blockJson("move-1", "MOVE_FORWARD", "{\"distanceMeters\":1.0}")},
              ${blockJson("move-2", "MOVE_FORWARD", "{\"distanceMeters\":2.0}")}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("move-1", "move-2"), program.blocks.map { it.id })
        assertEquals(BlockParameters.DistanceMeters(2.0), program.blocks[1].parameters)
    }

    @Test
    fun `minimal start end program serializes with empty parameter objects`() {
        val program = BlockProgram(
            schemaVersion = 1,
            blocks = listOf(
                team.inreok.poppyserver.domain.block.model.BlockInstance("start", BlockType.START, BlockParameters.None),
                team.inreok.poppyserver.domain.block.model.BlockInstance("end", BlockType.END, BlockParameters.None),
            ),
        )

        val json = serializer.serialize(program)
        assertEquals("{}", objectMapper.readTree(json)["blocks"][0]["parameters"].toString())
        assertEquals(false, objectMapper.readTree(json)["blocks"][0].has("children"))
    }

    @Test
    fun `contract rejects malformed and unsupported roots`() {
        assertCode("{", BlockProgramParseErrorCode.MALFORMED_JSON)
        assertCode("[]", BlockProgramParseErrorCode.MALFORMED_JSON)
        assertCode("{\"blocks\":[]}", BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode("{\"schemaVersion\":\"1\",\"blocks\":[]}", BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode("{\"schemaVersion\":2,\"blocks\":[]}", BlockProgramParseErrorCode.UNSUPPORTED_SCHEMA_VERSION)
        assertCode("{\"schemaVersion\":1}", BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode("{\"schemaVersion\":1,\"blocks\":{}}", BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode("{\"schemaVersion\":1,\"blocks\":[],\"extra\":true}", BlockProgramParseErrorCode.INVALID_FIELD)
    }

    @Test
    fun `contract rejects invalid block fields`() {
        assertCode(programJson("{\"type\":\"STOP\",\"parameters\":{}}"), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson(blockJson(" ", "STOP", "{}")), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson("{\"id\":\"x\",\"parameters\":{}}"), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson(blockJson("x", "UNKNOWN", "{}")), BlockProgramParseErrorCode.UNKNOWN_BLOCK_TYPE)
        assertCode(programJson("{\"id\":\"x\",\"type\":\"STOP\"}"), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson("{\"id\":\"x\",\"type\":\"STOP\",\"parameters\":null}"), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson("{\"id\":\"x\",\"type\":\"STOP\",\"parameters\":[]}"), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson("{\"id\":\"x\",\"type\":\"STOP\",\"parameters\":{},\"children\":[]}"), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson("{\"id\":\"x\",\"type\":\"REPEAT\",\"parameters\":{\"count\":1}}"), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson("{\"id\":\"x\",\"type\":\"REPEAT\",\"parameters\":{\"count\":1},\"children\":{}}"), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson("{\"id\":\"x\",\"type\":\"REPEAT\",\"parameters\":{\"count\":1},\"children\":[1]}"), BlockProgramParseErrorCode.INVALID_FIELD)
    }

    @Test
    fun `contract rejects invalid parameter names and json types`() {
        assertCode(programJson(blockJson("x", "WAIT", "{}")), BlockProgramParseErrorCode.INVALID_PARAMETER)
        assertCode(programJson(blockJson("x", "WAIT", "{\"durationSeconds\":\"1.0\"}")), BlockProgramParseErrorCode.INVALID_PARAMETER)
        assertCode(programJson(blockJson("x", "WAIT", "{\"durationSeconds\":1,\"angleDegrees\":90}")), BlockProgramParseErrorCode.INVALID_FIELD)
        assertCode(programJson(blockJsonWithChildren("x", "REPEAT", "{\"count\":3.5}")), BlockProgramParseErrorCode.INVALID_PARAMETER)
        assertCode(programJson(blockJsonWithChildren("x", "REPEAT", "{\"count\":\"3\"}")), BlockProgramParseErrorCode.INVALID_PARAMETER)
        assertCode(programJson(blockJson("x", "PRESET", "{\"presetCode\":123}")), BlockProgramParseErrorCode.INVALID_PARAMETER)
        assertCode(programJson(blockJson("x", "MOVE_FORWARD", "{\"distanceMeters\":1,\"angleDegrees\":90}")), BlockProgramParseErrorCode.INVALID_FIELD)
    }

    private fun assertCode(document: String, expected: BlockProgramParseErrorCode) {
        val exception = assertFailsWith<BlockProgramParseException> { parser.parse(document) }
        assertEquals(expected, exception.code)
    }

    private fun programJson(block: String): String = "{\"schemaVersion\":1,\"blocks\":[$block]}"

    private fun blockJson(id: String, type: String, parameters: String): String =
        "{\"id\":\"$id\",\"type\":\"$type\",\"parameters\":$parameters}"

    private fun blockJsonWithChildren(id: String, type: String, parameters: String): String =
        blockJson(id, type, parameters).dropLast(1) + ",\"children\":[]}"

    private fun parametersFor(type: BlockType): String = when (type) {
        BlockType.WAIT -> "{\"durationSeconds\":1.5}"
        BlockType.REPEAT -> "{\"count\":2}"
        BlockType.MOVE_FORWARD, BlockType.MOVE_BACKWARD -> "{\"distanceMeters\":1.0}"
        BlockType.TURN_LEFT, BlockType.TURN_RIGHT -> "{\"angleDegrees\":90.0}"
        BlockType.PRESET -> "{\"presetCode\":\"SAFE\"}"
        BlockType.START, BlockType.END, BlockType.STOP, BlockType.SIT, BlockType.STAND -> "{}"
    }
}
