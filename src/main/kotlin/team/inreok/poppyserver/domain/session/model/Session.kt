package team.inreok.poppyserver.domain.session.model

import java.time.Instant
import java.util.UUID

class Session private constructor(
    val id: UUID,
    currentBlockVersionValue: Long,
    val createdAt: Instant,
    sessionTokenDigestValue: String?,
    recoveryCodeDigestValue: String?,
    lastActivityAtValue: Instant,
    var expiredAt: Instant?,
    modeValue: ExperienceMode?,
    missionIdValue: UUID?,
) {

    var sessionTokenDigest: String? = sessionTokenDigestValue
        private set

    var recoveryCodeDigest: String? = recoveryCodeDigestValue
        private set

    var currentBlockVersion: Long = currentBlockVersionValue
        private set

    var lastActivityAt: Instant = lastActivityAtValue
        private set

    var mode: ExperienceMode? = modeValue
        private set

    var missionId: UUID? = missionIdValue
        private set

    val status: SessionStatus
        get() = if (expiredAt == null) SessionStatus.ACTIVE else SessionStatus.EXPIRED

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
            sessionTokenDigestValue = null,
            recoveryCodeDigestValue = null,
            lastActivityAtValue = createdAt,
            expiredAt = null,
            modeValue = null,
            missionIdValue = null,
        )

        fun createWithToken(
            sessionTokenDigest: String,
            recoveryCodeDigest: String? = null,
            createdAt: Instant = Instant.now(),
        ): Session = Session(
            id = UUID.randomUUID(),
            currentBlockVersionValue = 0,
            createdAt = createdAt,
            sessionTokenDigestValue = sessionTokenDigest,
            recoveryCodeDigestValue = recoveryCodeDigest,
            lastActivityAtValue = createdAt,
            expiredAt = null,
            modeValue = null,
            missionIdValue = null,
        )

        fun restore(
            id: UUID,
            currentBlockVersion: Long,
            createdAt: Instant,
            sessionTokenDigest: String? = null,
            recoveryCodeDigest: String? = null,
            lastActivityAt: Instant? = null,
            expiredAt: Instant? = null,
            mode: ExperienceMode? = null,
            missionId: UUID? = null,
        ): Session = Session(
            id = id,
            currentBlockVersionValue = currentBlockVersion,
            createdAt = createdAt,
            sessionTokenDigestValue = sessionTokenDigest,
            recoveryCodeDigestValue = recoveryCodeDigest,
            lastActivityAtValue = lastActivityAt ?: createdAt,
            expiredAt = expiredAt,
            modeValue = mode,
            missionIdValue = missionId,
        )
    }

    fun rotateSessionToken(newDigest: String) {
        sessionTokenDigest = newDigest
    }

    fun rotateCredentials(newSessionTokenDigest: String, newRecoveryCodeDigest: String) {
        sessionTokenDigest = newSessionTokenDigest
        recoveryCodeDigest = newRecoveryCodeDigest
    }
}
