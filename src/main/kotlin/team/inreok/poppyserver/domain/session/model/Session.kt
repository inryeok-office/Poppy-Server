package team.inreok.poppyserver.domain.session.model

import java.time.Instant
import java.util.UUID

class Session private constructor(
    val id: UUID,
    currentBlockVersionValue: Long,
    val createdAt: Instant,
) {

    var currentBlockVersion: Long = currentBlockVersionValue
        private set

    fun advanceBlockVersion(): Long {
        currentBlockVersion += 1
        return currentBlockVersion
    }

    companion object {
        fun create(createdAt: Instant = Instant.now()): Session = Session(
            id = UUID.randomUUID(),
            currentBlockVersionValue = 0,
            createdAt = createdAt,
        )

        fun restore(
            id: UUID,
            currentBlockVersion: Long,
            createdAt: Instant,
        ): Session = Session(
            id = id,
            currentBlockVersionValue = currentBlockVersion,
            createdAt = createdAt,
        )
    }
}
