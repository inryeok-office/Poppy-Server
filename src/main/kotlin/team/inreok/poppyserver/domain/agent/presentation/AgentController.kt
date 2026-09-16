package team.inreok.poppyserver.domain.agent.presentation

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import team.inreok.poppyserver.domain.agent.application.AgentManagementService
import team.inreok.poppyserver.domain.agent.application.AgentRegistrationResult
import team.inreok.poppyserver.domain.agent.application.HeartbeatCommand
import team.inreok.poppyserver.domain.agent.application.HeartbeatRobotCommand
import team.inreok.poppyserver.domain.agent.application.RegisterAgentCommand
import team.inreok.poppyserver.domain.agent.application.RegisterAgentRobotCommand
import team.inreok.poppyserver.domain.agent.model.Agent
import team.inreok.poppyserver.domain.execution.application.AgentExecutionDelivery
import team.inreok.poppyserver.domain.execution.application.AgentExecutionDeliveryService
import team.inreok.poppyserver.domain.execution.application.AgentExecutionStatusService
import team.inreok.poppyserver.domain.execution.application.ExecutionStatusReport
import team.inreok.poppyserver.domain.execution.application.ReportExecutionStatus
import team.inreok.poppyserver.domain.execution.application.ReportExecutionStatusCommand
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/internal/agents")
class AgentController(
    private val agentManagementService: AgentManagementService,
    private val agentExecutionDeliveryService: AgentExecutionDeliveryService,
    private val agentExecutionStatusService: AgentExecutionStatusService,
) {
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: AgentRegistrationRequest,
    ): ResponseEntity<ApiResponse<AgentRegistrationResponse>> {
        val result = agentManagementService.register(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(result.toResponse()),
        )
    }

    @PostMapping("/{agentId}/heartbeat")
    fun heartbeat(
        @PathVariable agentId: UUID,
        @Valid @RequestBody request: AgentHeartbeatRequest,
    ): ApiResponse<AgentHeartbeatResponse> {
        val acceptedAt = agentManagementService.heartbeat(agentId, request.toCommand())
        return ApiResponse.success(
            AgentHeartbeatResponse(
                agentId = agentId,
                acceptedAt = acceptedAt.toUtcLocalDateTime(),
            ),
        )
    }

    @GetMapping("/{agentId}/executions/next")
    fun nextExecution(
        @PathVariable agentId: UUID,
        @RequestParam robotId: UUID,
    ): ApiResponse<AgentExecutionNextResponse> = ApiResponse.success(
        AgentExecutionNextResponse(
            execution = agentExecutionDeliveryService.findNext(agentId, robotId)?.toResponse(),
        ),
    )

    @PostMapping("/{agentId}/executions/{executionId}/status")
    fun reportExecutionStatus(
        @PathVariable agentId: UUID,
        @PathVariable executionId: UUID,
        @Valid @RequestBody request: AgentExecutionStatusRequest,
    ): ApiResponse<AgentExecutionStatusResponse> = ApiResponse.success(
        agentExecutionStatusService.report(agentId, executionId, request.toCommand()).toResponse(),
    )
}

data class AgentRegistrationRequest(
    @field:NotBlank val agentName: String?,
    @field:NotBlank val agentVersion: String?,
    @field:NotBlank val sdkVersion: String?,
    @field:NotBlank val platform: String?,
    @field:NotNull @field:Valid val robots: List<AgentRobotRegistrationRequest>?,
) {
    fun toCommand(): RegisterAgentCommand = RegisterAgentCommand(
        agentName = requireNotNull(agentName),
        agentVersion = requireNotNull(agentVersion),
        sdkVersion = requireNotNull(sdkVersion),
        platform = requireNotNull(platform),
        robots = requireNotNull(robots).map { it.toCommand() },
    )
}

data class AgentRobotRegistrationRequest(
    @field:NotNull val robotId: UUID?,
    @field:NotBlank val model: String?,
    @field:NotBlank val edition: String?,
    @field:NotBlank val firmwareVersion: String?,
    @field:NotNull val capabilities: List<@NotBlank String>?,
) {
    fun toCommand(): RegisterAgentRobotCommand = RegisterAgentRobotCommand(
        robotId = robotId,
        model = requireNotNull(model),
        edition = requireNotNull(edition),
        firmwareVersion = requireNotNull(firmwareVersion),
        capabilityCodes = requireNotNull(capabilities).map { it.trim().uppercase() }.toSet(),
    )
}

data class AgentHeartbeatRequest(
    @field:NotNull val sentAt: LocalDateTime?,
    @field:NotNull @field:Valid val robots: List<AgentHeartbeatRobotRequest>?,
) {
    fun toCommand(): HeartbeatCommand = HeartbeatCommand(
        sentAt = requireNotNull(sentAt).toInstant(ZoneOffset.UTC),
        robots = requireNotNull(robots).map { it.toCommand() },
    )
}

data class AgentHeartbeatRobotRequest(
    @field:NotNull val robotId: UUID?,
    @field:NotNull val connectionStatus: RobotConnectionStatus?,
    @field:NotNull val operationalStatus: RobotOperationStatus?,
    @field:Min(0) @field:Max(100) val batteryPercent: Int? = null,
    val currentExecutionId: JsonNode? = null,
) {
    fun toCommand(): HeartbeatRobotCommand = HeartbeatRobotCommand(
        robotId = requireNotNull(robotId),
        connectionStatus = requireNotNull(connectionStatus),
        operationStatus = requireNotNull(operationalStatus),
        batteryPercent = batteryPercent,
        currentExecutionId = currentExecutionId.toNullableUuid(),
        currentExecutionIdProvided = currentExecutionId != null,
    )
}

data class AgentRegistrationResponse(
    val agentId: UUID,
    val registeredAt: LocalDateTime,
    val acceptedRobotIds: List<UUID>,
    val agentToken: String,
)

data class AgentHeartbeatResponse(
    val agentId: UUID,
    val acceptedAt: LocalDateTime,
)

data class AgentExecutionNextResponse(
    val execution: AgentExecutionResponse?,
)

data class AgentExecutionResponse(
    val executionId: UUID,
    val robotId: UUID,
    val status: String,
    val protocolVersion: Int,
    val commandPayload: String,
)

data class AgentExecutionStatusRequest(
    @field:NotNull val robotId: UUID?,
    @field:NotBlank val status: String?,
) {
    fun toCommand(): ReportExecutionStatusCommand {
        val requestedStatus = try {
            ReportExecutionStatus.valueOf(requireNotNull(status))
        } catch (_: IllegalArgumentException) {
            throw ApplicationException(ErrorCode.EXECUTION_STATUS_UNSUPPORTED)
        }
        return ReportExecutionStatusCommand(
            robotId = requireNotNull(robotId),
            status = requestedStatus,
        )
    }
}

data class AgentExecutionStatusResponse(
    val executionId: UUID,
    val robotId: UUID,
    val status: String,
)

private fun AgentRegistrationResult.toResponse(): AgentRegistrationResponse = AgentRegistrationResponse(
    agentId = agent.id,
    registeredAt = agent.registeredAt.toUtcLocalDateTime(),
    acceptedRobotIds = acceptedRobotIds,
    agentToken = agentToken,
)

private fun AgentExecutionDelivery.toResponse(): AgentExecutionResponse = AgentExecutionResponse(
    executionId = executionId,
    robotId = robotId,
    status = status.name,
    protocolVersion = protocolVersion,
    commandPayload = commandPayload,
)

private fun ExecutionStatusReport.toResponse(): AgentExecutionStatusResponse = AgentExecutionStatusResponse(
    executionId = executionId,
    robotId = robotId,
    status = status.name,
)

private fun Instant.toUtcLocalDateTime(): LocalDateTime = atZone(ZoneOffset.UTC).toLocalDateTime()

private fun JsonNode?.toNullableUuid(): UUID? {
    if (this == null || isNull) return null
    if (!isTextual) throw ApplicationException(ErrorCode.HEARTBEAT_PAYLOAD_INVALID)
    return try {
        UUID.fromString(asText())
    } catch (_: IllegalArgumentException) {
        throw ApplicationException(ErrorCode.HEARTBEAT_PAYLOAD_INVALID)
    }
}
