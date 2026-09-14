package team.inreok.poppyserver.domain.session.model

import java.time.Instant
import java.util.UUID

class BlockRevision private constructor(
    val sessionId: UUID,
    val version: Long,
    val document: String,
    val createdAt: Instant,
) {

    companion object {
        fun create(
            sessionId: UUID,
            version: Long,
            document: String,
            createdAt: Instant = Instant.now(),
        ): BlockRevision = BlockRevision(
            sessionId = sessionId,
            version = version,
            document = document,
            createdAt = createdAt,
        )
    }
}
