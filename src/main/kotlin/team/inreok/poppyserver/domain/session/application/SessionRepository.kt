package team.inreok.poppyserver.domain.session.application

import java.time.Instant
import java.util.UUID
import team.inreok.poppyserver.domain.session.model.Session

interface SessionRepository {
    fun save(session: Session): Session

    fun findById(id: UUID): Session?

    fun findByIdForUpdate(id: UUID): Session?

    fun findByTokenDigest(tokenDigest: String): Session?

    fun findByRecoveryCodeDigest(recoveryCodeDigest: String): Session?

    fun findInactiveIdsBefore(cutoff: Instant): List<UUID>
}
