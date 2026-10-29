package team.inreok.poppyserver.domain.block.application

import org.junit.jupiter.api.Test
import team.inreok.poppyserver.domain.block.model.BlockType
import team.inreok.poppyserver.domain.block.model.ParameterType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockDefinitionCatalogTest {
    private val definitions = BlockDefinitionCatalog().list()

    @Test
    fun `catalog contains every Block Program v1 type in stable order`() {
        assertEquals(BlockType.entries, definitions.map { it.type })
        assertEquals(definitions.size, definitions.map { it.type }.toSet().size)
    }

    @Test
    fun `parameter metadata matches the Block Program v1 contract`() {
        val byType = definitions.associateBy { it.type }

        assertParameter(byType[BlockType.WAIT], "durationSeconds", ParameterType.NUMBER, "s")
        assertParameter(byType[BlockType.REPEAT], "count", ParameterType.INTEGER, "count")
        assertParameter(byType[BlockType.MOVE_FORWARD], "distanceMeters", ParameterType.NUMBER, "m")
        assertParameter(byType[BlockType.MOVE_BACKWARD], "distanceMeters", ParameterType.NUMBER, "m")
        assertParameter(byType[BlockType.TURN_LEFT], "angleDegrees", ParameterType.NUMBER, "deg")
        assertParameter(byType[BlockType.TURN_RIGHT], "angleDegrees", ParameterType.NUMBER, "deg")
        assertParameter(byType[BlockType.PRESET], "presetCode", ParameterType.ENUM, null)

        definitions.filter { it.type.parameterName == null }.forEach { definition ->
            assertTrue(definition.parameters.isEmpty())
        }
        assertTrue(definitions.none { definition -> definition.parameters.any { it.name.contains("velocity", ignoreCase = true) } })
    }

    @Test
    fun `parameter ranges match the Agent and Client execution limits`() {
        val byType = definitions.associateBy { it.type }

        assertRange(byType.getValue(BlockType.WAIT), 1.0, 10.0)
        assertRange(byType.getValue(BlockType.REPEAT), 1.0, 10.0)
        assertRange(byType.getValue(BlockType.MOVE_FORWARD), 0.1, 3.0)
        assertRange(byType.getValue(BlockType.MOVE_BACKWARD), 0.1, 3.0)
        assertRange(byType.getValue(BlockType.TURN_LEFT), 1.0, 360.0)
        assertRange(byType.getValue(BlockType.TURN_RIGHT), 1.0, 360.0)
    }

    @Test
    fun `PRESET is available with the allow-listed codes and Korean labels`() {
        val preset = definitions.single { it.type == BlockType.PRESET }
        val parameter = preset.parameters.single()

        assertTrue(preset.available)
        assertEquals(listOf("HELLO", "HEART", "DANCE1", "STRETCH"), parameter.options)
        assertEquals(
            mapOf("HELLO" to "인사하기", "HEART" to "하트", "DANCE1" to "춤추기", "STRETCH" to "스트레칭"),
            parameter.optionLabels,
        )
    }

    private fun assertRange(
        definition: team.inreok.poppyserver.domain.block.model.BlockDefinition,
        min: Double,
        max: Double,
    ) {
        val parameter = definition.parameters.single()
        assertEquals(min, parameter.min)
        assertEquals(max, parameter.max)
    }

    private fun assertParameter(
        definition: team.inreok.poppyserver.domain.block.model.BlockDefinition?,
        name: String,
        type: ParameterType,
        unit: String?,
    ) {
        val parameter = requireNotNull(definition).parameters.single()
        assertEquals(name, parameter.name)
        assertEquals(type, parameter.type)
        assertEquals(unit, parameter.unit)
        assertTrue(parameter.required)
    }
}
