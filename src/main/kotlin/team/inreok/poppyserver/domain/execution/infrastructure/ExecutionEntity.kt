package team.inreok.poppyserver.domain.execution.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

@Entity
@Table(name = "executions")
class ExecutionEntity(
    @Id
    var id: UUID? = null,
    @Column(nullable = false, columnDefinition = "text")
    @Enumerated(EnumType.STRING)
    var status: ExecutionStatus = ExecutionStatus.QUEUED,
)
