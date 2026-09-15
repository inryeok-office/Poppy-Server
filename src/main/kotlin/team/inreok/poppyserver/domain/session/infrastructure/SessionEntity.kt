package team.inreok.poppyserver.domain.session.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sessions")
class SessionEntity(
    @Id
    var id: UUID? = null,
    @Column(name = "current_block_version", nullable = false)
    var currentBlockVersion: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "session_token_digest", unique = true)
    var sessionTokenDigest: String? = null,
)
