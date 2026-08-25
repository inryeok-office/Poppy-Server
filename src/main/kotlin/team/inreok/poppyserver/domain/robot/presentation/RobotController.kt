package team.inreok.poppyserver.domain.robot.presentation

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.robot.application.RegisterRobotCommand
import team.inreok.poppyserver.domain.robot.application.RobotManagementService
import team.inreok.poppyserver.domain.robot.application.UpdateRobotCommand
import team.inreok.poppyserver.domain.robot.model.CapabilitySupportStatus
import team.inreok.poppyserver.domain.robot.model.Robot
import team.inreok.poppyserver.domain.robot.model.RobotCapability
import team.inreok.poppyserver.domain.robot.model.RobotConnectionStatus
import team.inreok.poppyserver.domain.robot.model.RobotOperationStatus
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@ConditionalOnBean(RobotManagementService::class)
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/admin/robots")
class RobotController(
    private val robotManagementService: RobotManagementService,
) {
    @PostMapping
    fun register(@Valid @RequestBody request: RobotRegistrationRequest): ResponseEntity<ApiResponse<RobotDetailResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(robotManagementService.register(request.toCommand()).toDetailResponse()),
        )

    @GetMapping
    fun findAll(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) connectionStatus: String?,
    ): ApiResponse<RobotListResponse> {
        val operationStatus = status?.let { parseOperationStatus(it) }
        val robotConnectionStatus = connectionStatus?.let { parseConnectionStatus(it) }
        return ApiResponse.success(
            RobotListResponse(
                robotManagementService.findAll(operationStatus, robotConnectionStatus)
                    .map { it.toListItemResponse() },
            ),
        )
    }

    @PatchMapping("/{robotId}")
    fun update(
        @PathVariable robotId: UUID,
        @Valid @RequestBody request: RobotUpdateRequest,
    ): ApiResponse<RobotDetailResponse> =
        ApiResponse.success(robotManagementService.update(robotId, request.toCommand()).toDetailResponse())

    private fun parseOperationStatus(value: String): RobotOperationStatus = try {
        RobotOperationStatus.valueOf(value)
    } catch (_: IllegalArgumentException) {
        throw ApplicationException(ErrorCode.ROBOT_FILTER_INVALID)
    }

    private fun parseConnectionStatus(value: String): RobotConnectionStatus = try {
        RobotConnectionStatus.valueOf(value)
    } catch (_: IllegalArgumentException) {
        throw ApplicationException(ErrorCode.ROBOT_FILTER_INVALID)
    }
}

data class RobotRegistrationRequest(
    @field:NotBlank val alias: String?,
    @field:NotBlank val model: String?,
    @field:NotBlank val edition: String?,
    @field:NotBlank val firmwareVersion: String?,
    @field:NotBlank val sdkVersion: String?,
    val agentId: UUID?,
    @field:NotNull val capabilities: List<@Valid RobotCapabilityRequest>?,
    val safetyProfileId: UUID?,
    @field:NotNull val isExternal: Boolean?,
) {
    fun toCommand(): RegisterRobotCommand = RegisterRobotCommand(
        alias = requireNotNull(alias),
        model = requireNotNull(model),
        edition = requireNotNull(edition),
        firmwareVersion = firmwareVersion,
        sdkVersion = sdkVersion,
        agentId = agentId,
        capabilities = requireNotNull(capabilities).map { it.toModel() },
        safetyProfileId = safetyProfileId,
        isExternal = requireNotNull(isExternal),
    )
}

data class RobotUpdateRequest(
    @field:NotBlank val alias: String? = null,
    @field:NotBlank val firmwareVersion: String? = null,
    @field:NotBlank val sdkVersion: String? = null,
    @field:NotNull val capabilities: List<@Valid RobotCapabilityRequest>? = null,
    val safetyProfileId: UUID? = null,
    val operationalStatus: RobotOperationStatus? = null,
) {
    fun toCommand(): UpdateRobotCommand = UpdateRobotCommand(
        alias = alias,
        firmwareVersion = firmwareVersion,
        sdkVersion = sdkVersion,
        capabilities = requireNotNull(capabilities).map { it.toModel() },
        safetyProfileId = safetyProfileId,
        operationStatus = operationalStatus,
    )
}

data class RobotCapabilityRequest(
    @field:NotBlank val code: String?,
    val status: CapabilitySupportStatus? = null,
) {
    fun toModel(): RobotCapability = RobotCapability(
        code = requireNotNull(code),
        status = status ?: CapabilitySupportStatus.UNVERIFIED,
    )
}

data class RobotDetailResponse(
    val robotId: UUID,
    val alias: String,
    val model: String,
    val edition: String,
    val firmwareVersion: String?,
    val sdkVersion: String?,
    val capabilities: List<RobotCapabilityResponse>,
    val connectionStatus: RobotConnectionStatus,
    val operationalStatus: RobotOperationStatus,
)

data class RobotListResponse(
    val robots: List<RobotListItemResponse>,
)

data class RobotListItemResponse(
    val robotId: UUID,
    val alias: String,
    val model: String,
    val edition: String,
    val capabilities: List<RobotCapabilityResponse>,
    val connectionStatus: RobotConnectionStatus,
    val operationalStatus: RobotOperationStatus,
    val occupied: Boolean,
    val currentExecutionId: UUID?,
    val lastHeartbeatAt: LocalDateTime?,
)

data class RobotCapabilityResponse(
    val code: String,
    val status: CapabilitySupportStatus,
)

private fun Robot.toDetailResponse(): RobotDetailResponse = RobotDetailResponse(
    robotId = id,
    alias = alias,
    model = model,
    edition = edition,
    firmwareVersion = firmwareVersion,
    sdkVersion = sdkVersion,
    capabilities = capabilities.values.map { RobotCapabilityResponse(it.code, it.status) },
    connectionStatus = connectionStatus,
    operationalStatus = operationStatus,
)

private fun Robot.toListItemResponse(): RobotListItemResponse = RobotListItemResponse(
    robotId = id,
    alias = alias,
    model = model,
    edition = edition,
    capabilities = capabilities.values.map { RobotCapabilityResponse(it.code, it.status) },
    connectionStatus = connectionStatus,
    operationalStatus = operationStatus,
    occupied = occupied,
    currentExecutionId = currentExecutionId,
    lastHeartbeatAt = lastHeartbeatAt?.atZone(ZoneOffset.UTC)?.toLocalDateTime(),
)
