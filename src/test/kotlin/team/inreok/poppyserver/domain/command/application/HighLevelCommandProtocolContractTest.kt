package team.inreok.poppyserver.domain.command.application

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import team.inreok.poppyserver.domain.command.model.CommandType
import team.inreok.poppyserver.domain.command.model.HighLevelCommand
import team.inreok.poppyserver.domain.command.model.HighLevelCommandParameters
import team.inreok.poppyserver.domain.command.model.HighLevelCommandProgram
import team.inreok.poppyserver.domain.command.model.MoveDirection
import team.inreok.poppyserver.domain.command.model.Posture
import team.inreok.poppyserver.domain.command.model.TurnDirection
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HighLevelCommandProtocolContractTest {
    private val objectMapper = JsonMapper.builder().build()
    private val serializer = HighLevelCommandProtocolSerializer(objectMapper)
    private val parser = HighLevelCommandProtocolParser(objectMapper)

    @Test
    fun `all command types serialize and round trip with typed parameters`() {
        val program = HighLevelCommandProgram(
            protocolVersion = 1,
            commands = listOf(
                command(0, "wait", CommandType.WAIT, HighLevelCommandParameters.Wait(1.5)),
                command(1, "move", CommandType.MOVE, HighLevelCommandParameters.Move(MoveDirection.FORWARD, 1.0)),
                command(2, "turn", CommandType.TURN, HighLevelCommandParameters.Turn(TurnDirection.LEFT, 90.0)),
                command(3, "stop", CommandType.STOP, HighLevelCommandParameters.None),
                command(4, "posture", CommandType.POSTURE, HighLevelCommandParameters.PostureCommand(Posture.SIT)),
                command(5, "preset", CommandType.PRESET, HighLevelCommandParameters.Preset("demo")),
            ),
        )

        val serialized = serializer.serialize(program)
        val parsed = parser.parse(serialized)

        assertEquals(program, parsed)
        assertEquals(program, parser.parse(serializer.serialize(parsed)))
    }

    @Test
    fun `empty command list is valid for a program without executable blocks`() {
        val program = HighLevelCommandProgram(protocolVersion = 1, commands = emptyList())

        assertEquals(program, parser.parse(serializer.serialize(program)))
    }

    @Test
    fun `parser rejects unknown fields and numeric coercion`() {
        assertFailsWith<HighLevelCommandProtocolException> {
            parser.parse("""{"protocolVersion":1,"commands":[],"extra":true}""")
        }
        assertFailsWith<HighLevelCommandProtocolException> {
            parser.parse(
                """{"protocolVersion":1,"commands":[{"sequence":0,"sourceBlockId":"move","type":"MOVE","parameters":{"direction":"FORWARD","distanceMeters":"1.0"}}]}""",
            )
        }
    }

    @Test
    fun `parser rejects invalid protocol type and sequence`() {
        assertEquals(
            HighLevelCommandProtocolErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
            assertFailsWith<HighLevelCommandProtocolException> {
                parser.parse("""{"protocolVersion":2,"commands":[]}""")
            }.code,
        )
        assertEquals(
            HighLevelCommandProtocolErrorCode.INVALID_SEQUENCE,
            assertFailsWith<HighLevelCommandProtocolException> {
                parser.parse(
                    """{"protocolVersion":1,"commands":[{"sequence":1,"sourceBlockId":"move","type":"STOP","parameters":{}}]}""",
                )
            }.code,
        )
    }

    @Test
    fun `serializer rejects mismatched typed parameters`() {
        assertFailsWith<HighLevelCommandProtocolException> {
            serializer.serialize(
                HighLevelCommandProgram(
                    protocolVersion = 1,
                    commands = listOf(command(0, "stop", CommandType.STOP, HighLevelCommandParameters.Wait(1.0))),
                ),
            )
        }
    }

    private fun command(
        sequence: Int,
        sourceBlockId: String,
        type: CommandType,
        parameters: HighLevelCommandParameters,
    ) = HighLevelCommand(sequence, sourceBlockId, type, parameters)
}
