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
        assertTrue(byType.getValue(BlockType.PRESET).parameters.single().options.isEmpty())
        assertTrue(definitions.all { definition -> definition.parameters.all { it.min == null && it.max == null } })
        assertTrue(definitions.none { definition -> definition.parameters.any { it.name.contains("velocity", ignoreCase = true) } })
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
