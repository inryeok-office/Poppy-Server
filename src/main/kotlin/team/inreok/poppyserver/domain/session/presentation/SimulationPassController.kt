package team.inreok.poppyserver.domain.session.presentation

import java.time.Instant
import java.util.UUID
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.session.application.SimulationPassRecordResult
import team.inreok.poppyserver.domain.session.application.SimulationPassService
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/sessions")
class SimulationPassController(
    private val simulationPassService: SimulationPassService,
) {
    @PostMapping("/{sessionId}/simulation-passes")
    fun recordSimulationPass(
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: SimulationPassRequest,
    ): ResponseEntity<ApiResponse<SimulationPassResponse>> {
        val result = simulationPassService.recordSimulationPass(sessionId, requireNotNull(request.blockVersion))
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(ApiResponse.success(result.toResponse()))
    }
}

data class SimulationPassRequest(
    @field:NotNull val blockVersion: Long?,
)

data class SimulationPassResponse(
    val sessionId: UUID,
    val blockVersion: Long,
    val passedAt: Instant,
)

private fun SimulationPassRecordResult.toResponse(): SimulationPassResponse = SimulationPassResponse(
    sessionId = sessionId,
    blockVersion = blockVersion,
    passedAt = passedAt,
)
