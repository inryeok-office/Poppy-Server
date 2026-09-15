package team.inreok.poppyserver.domain.execution.application

import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.execution.model.ExecutionStatus

interface ExecutionStatusEventPublisher {
    fun publish(event: ExecutionStatusChangedEvent)
}

data class ExecutionStatusChangedEvent(
    val executionId: UUID,
    val sessionId: UUID,
    val status: ExecutionStatus,
)

@Component
class SpringExecutionStatusEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : ExecutionStatusEventPublisher {
    override fun publish(event: ExecutionStatusChangedEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}
