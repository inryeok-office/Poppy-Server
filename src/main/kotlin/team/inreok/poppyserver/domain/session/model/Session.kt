package team.inreok.poppyserver.domain.session.model

import java.time.Instant
import java.util.UUID

class Session private constructor(
    val id: UUID,
    currentBlockVersionValue: Long,
    val createdAt: Instant,
    val sessionTokenDigest: String?,
    lastActivityAtValue: Instant,
    var expiredAt: Instant?,
) {

    var currentBlockVersion: Long = currentBlockVersionValue
        private set

    var lastActivityAt: Instant = lastActivityAtValue
        private set

    fun advanceBlockVersion(): Long {
        currentBlockVersion += 1
        return currentBlockVersion
    }

    fun touchActivity(at: Instant) {
        if (at.isAfter(lastActivityAt)) {
            lastActivityAt = at
        }
    }

    fun expire(at: Instant) {
        if (expiredAt == null) {
            expiredAt = at
        }
    }

    companion object {
        fun create(createdAt: Instant = Instant.now()): Session = Session(
            id = UUID.randomUUID(),
            currentBlockVersionValue = 0,
            createdAt = createdAt,
            sessionTokenDigest = null,
            lastActivityAtValue = createdAt,
            expiredAt = null,
        )

        fun createWithToken(sessionTokenDigest: String, createdAt: Instant = Instant.now()): Session = Session(
            id = UUID.randomUUID(),
            currentBlockVersionValue = 0,
            createdAt = createdAt,
            sessionTokenDigest = sessionTokenDigest,
            lastActivityAtValue = createdAt,
            expiredAt = null,
        )

        fun restore(
            id: UUID,
            currentBlockVersion: Long,
            createdAt: Instant,
            sessionTokenDigest: String? = null,
            lastActivityAt: Instant? = null,
            expiredAt: Instant? = null,
        ): Session = Session(
            id = id,
            currentBlockVersionValue = currentBlockVersion,
            createdAt = createdAt,
            sessionTokenDigest = sessionTokenDigest,
            lastActivityAtValue = lastActivityAt ?: createdAt,
            expiredAt = expiredAt,
        )
    }
}
