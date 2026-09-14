package team.inreok.poppyserver.domain.session.application

import java.util.UUID
import team.inreok.poppyserver.domain.session.model.BlockRevision

interface BlockRevisionRepository {
    fun save(revision: BlockRevision): BlockRevision

    fun findById(sessionId: UUID, version: Long): BlockRevision?
}
