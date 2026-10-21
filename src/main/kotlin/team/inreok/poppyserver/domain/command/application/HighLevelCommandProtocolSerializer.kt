package team.inreok.poppyserver.domain.command.application

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import team.inreok.poppyserver.domain.command.model.CommandType
import team.inreok.poppyserver.domain.command.model.HighLevelCommand
import team.inreok.poppyserver.domain.command.model.HighLevelCommandParameters
import team.inreok.poppyserver.domain.command.model.HighLevelCommandProgram

class HighLevelCommandProtocolSerializer(
    private val objectMapper: ObjectMapper,
) {
    fun serialize(program: HighLevelCommandProgram): String {
        validateProgram(program)
        val root = objectMapper.createObjectNode()
        root.put("protocolVersion", program.protocolVersion)
        val commands = root.putArray("commands")
        program.commands.forEach { commands.add(writeCommand(it)) }
        return objectMapper.writeValueAsString(root)
    }

    private fun validateProgram(program: HighLevelCommandProgram) {
        if (program.protocolVersion !in HighLevelCommandProtocolParser.SUPPORTED_PROTOCOL_VERSIONS) {
            throw HighLevelCommandProtocolException(
                HighLevelCommandProtocolErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
                "protocolVersion ${program.protocolVersion} is not supported",
            )
        }
        program.commands.forEachIndexed { index, command ->
            if (command.sequence != index) {
                throw HighLevelCommandProtocolException(
                    HighLevelCommandProtocolErrorCode.INVALID_SEQUENCE,
                    "sequence must be contiguous from zero",
                )
            }
            if (command.sourceBlockId.isBlank()) {
                throw HighLevelCommandProtocolException(
                    HighLevelCommandProtocolErrorCode.INVALID_FIELD,
                    "sourceBlockId must be a non-blank string",
                )
            }
            validateParameters(command, program.protocolVersion)
        }
    }

    private fun validateParameters(command: HighLevelCommand, protocolVersion: Int) {
        val valid = when (command.type) {
            CommandType.WAIT -> command.parameters is HighLevelCommandParameters.Wait &&
                command.parameters.durationSeconds.isFinite()

            CommandType.MOVE -> when (protocolVersion) {
                1 -> command.parameters is HighLevelCommandParameters.Move &&
                    command.parameters.distanceMeters.isFinite()
                2 -> command.parameters is HighLevelCommandParameters.VelocityMove &&
                    command.parameters.linearVelocityMetersPerSecond.isFinite() &&
                    command.parameters.linearVelocityMetersPerSecond != 0.0 &&
                    command.parameters.durationSeconds.isFinite() && command.parameters.durationSeconds > 0.0
                else -> false
            }

            CommandType.TURN -> when (protocolVersion) {
                1 -> command.parameters is HighLevelCommandParameters.Turn &&
                    command.parameters.angleDegrees.isFinite()
                2 -> command.parameters is HighLevelCommandParameters.VelocityTurn &&
                    command.parameters.angularVelocityDegreesPerSecond.isFinite() &&
                    command.parameters.angularVelocityDegreesPerSecond != 0.0 &&
                    command.parameters.durationSeconds.isFinite() && command.parameters.durationSeconds > 0.0
                else -> false
            }

            CommandType.STOP -> command.parameters == HighLevelCommandParameters.None
            CommandType.POSTURE -> command.parameters is HighLevelCommandParameters.PostureCommand
            CommandType.PRESET -> command.parameters is HighLevelCommandParameters.Preset &&
                command.parameters.presetCode.isNotBlank()
        }
        if (!valid) {
            throw HighLevelCommandProtocolException(
                HighLevelCommandProtocolErrorCode.INVALID_PARAMETER,
                "${command.type.name} parameters do not match the command type",
            )
        }
    }

    private fun writeCommand(command: HighLevelCommand): ObjectNode = objectMapper.createObjectNode().apply {
        put("sequence", command.sequence)
        put("sourceBlockId", command.sourceBlockId)
        put("type", command.type.name)
        val parameters = putObject("parameters")
        writeParameters(command.parameters, parameters)
    }

    private fun writeParameters(parameters: HighLevelCommandParameters, node: ObjectNode) {
        when (parameters) {
            HighLevelCommandParameters.None -> Unit
            is HighLevelCommandParameters.Wait -> node.put("durationSeconds", parameters.durationSeconds)
            is HighLevelCommandParameters.Move -> {
                node.put("direction", parameters.direction.name)
                node.put("distanceMeters", parameters.distanceMeters)
            }

            is HighLevelCommandParameters.VelocityMove -> {
                node.put("linearVelocityMetersPerSecond", parameters.linearVelocityMetersPerSecond)
                node.put("durationSeconds", parameters.durationSeconds)
            }

            is HighLevelCommandParameters.Turn -> {
                node.put("direction", parameters.direction.name)
                node.put("angleDegrees", parameters.angleDegrees)
            }

            is HighLevelCommandParameters.VelocityTurn -> {
                node.put("angularVelocityDegreesPerSecond", parameters.angularVelocityDegreesPerSecond)
                node.put("durationSeconds", parameters.durationSeconds)
            }

            is HighLevelCommandParameters.PostureCommand -> node.put("posture", parameters.posture.name)
            is HighLevelCommandParameters.Preset -> node.put("presetCode", parameters.presetCode)
        }
    }
}
