package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import team.inreok.poppyserver.domain.execution.model.Execution

interface ExecutionRepository {
    fun save(execution: Execution): Execution

    fun findById(id: UUID): Execution?
}
