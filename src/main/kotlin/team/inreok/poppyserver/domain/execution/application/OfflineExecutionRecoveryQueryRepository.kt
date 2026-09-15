package team.inreok.poppyserver.domain.execution.application

import java.util.UUID

interface OfflineExecutionRecoveryQueryRepository {
    fun findCandidates(limit: Int): List<OfflineExecutionRecoveryCandidate>
}

data class OfflineExecutionRecoveryCandidate(
    val robotId: UUID,
    val executionId: UUID,
)
