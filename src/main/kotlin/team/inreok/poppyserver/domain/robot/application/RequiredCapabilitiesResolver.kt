package team.inreok.poppyserver.domain.robot.application

import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.command.model.CommandType
import team.inreok.poppyserver.domain.command.model.HighLevelCommandProgram

@Component
class RequiredCapabilitiesResolver {
    fun resolve(program: HighLevelCommandProgram): Set<String> = program.commands
        .mapNotNull { command ->
            when (command.type) {
                CommandType.WAIT -> null
                CommandType.MOVE -> ExecutionCapabilityCode.MOVE.code
                CommandType.TURN -> ExecutionCapabilityCode.TURN.code
                CommandType.STOP -> ExecutionCapabilityCode.STOP.code
                CommandType.POSTURE -> ExecutionCapabilityCode.POSTURE.code
                CommandType.PRESET -> ExecutionCapabilityCode.PRESET.code
            }
        }
        .toSet()
}
