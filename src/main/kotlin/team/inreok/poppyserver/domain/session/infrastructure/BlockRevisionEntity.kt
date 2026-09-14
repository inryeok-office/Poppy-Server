package team.inreok.poppyserver.domain.session.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "block_revisions")
@IdClass(BlockRevisionEntityId::class)
class BlockRevisionEntity(
    @Id
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID? = null,
    @Id
    @Column(nullable = false)
    var version: Long = 0,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var document: String = "{}",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
)

data class BlockRevisionEntityId(
    var sessionId: UUID? = null,
    var version: Long = 0,
) : Serializable
