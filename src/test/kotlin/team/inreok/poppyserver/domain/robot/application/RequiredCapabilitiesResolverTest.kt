package team.inreok.poppyserver.domain.robot.application

import org.junit.jupiter.api.Test
import team.inreok.poppyserver.domain.command.model.CommandType
import team.inreok.poppyserver.domain.command.model.HighLevelCommand
import team.inreok.poppyserver.domain.command.model.HighLevelCommandParameters
import team.inreok.poppyserver.domain.command.model.HighLevelCommandProgram
import team.inreok.poppyserver.domain.command.model.MoveDirection
import team.inreok.poppyserver.domain.command.model.Posture
import team.inreok.poppyserver.domain.command.model.TurnDirection
import kotlin.test.assertEquals

class RequiredCapabilitiesResolverTest {
    private val resolver = RequiredCapabilitiesResolver()

    @Test
    fun `WAIT and empty programs require no capability`() {
        assertEquals(emptySet(), resolver.resolve(program()))
        assertEquals(emptySet(), resolver.resolve(program(command(0, "wait", CommandType.WAIT, HighLevelCommandParameters.Wait(1.0)))))
    }

    @Test
    fun `each executable command maps to its stable capability code`() {
        val commands = listOf(
            command(0, "move", CommandType.MOVE, HighLevelCommandParameters.Move(MoveDirection.FORWARD, 1.0)),
            command(1, "turn", CommandType.TURN, HighLevelCommandParameters.Turn(TurnDirection.LEFT, 90.0)),
            command(2, "stop", CommandType.STOP, HighLevelCommandParameters.None),
            command(3, "posture", CommandType.POSTURE, HighLevelCommandParameters.PostureCommand(Posture.SIT)),
            command(4, "preset", CommandType.PRESET, HighLevelCommandParameters.Preset("demo")),
        )

        assertEquals(
            setOf("COMMAND_MOVE", "COMMAND_TURN", "COMMAND_STOP", "COMMAND_POSTURE", "COMMAND_PRESET"),
            resolver.resolve(program(*commands.toTypedArray())),
        )
    }

    @Test
    fun `repeated command types produce one capability and order does not affect the set`() {
        val move = command(0, "move", CommandType.MOVE, HighLevelCommandParameters.Move(MoveDirection.BACKWARD, 2.0))
        val turn = command(1, "turn", CommandType.TURN, HighLevelCommandParameters.Turn(TurnDirection.RIGHT, 45.0))

        val first = resolver.resolve(program(move, turn, move.copy(sequence = 2, sourceBlockId = "move-2")))
        val second = resolver.resolve(program(turn.copy(sequence = 0), move.copy(sequence = 1)))

        assertEquals(setOf("COMMAND_MOVE", "COMMAND_TURN"), first)
        assertEquals(first, second)
    }

    private fun program(vararg commands: HighLevelCommand) = HighLevelCommandProgram(1, commands.toList())

    private fun command(
        sequence: Int,
        sourceBlockId: String,
        type: CommandType,
        parameters: HighLevelCommandParameters,
    ) = HighLevelCommand(sequence, sourceBlockId, type, parameters)
}
