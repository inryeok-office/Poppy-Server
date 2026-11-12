package team.inreok.poppyserver.domain.arena.presentation

import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.arena.application.ArenaSettingsService
import team.inreok.poppyserver.domain.arena.application.ArenaSettingsUpdate
import team.inreok.poppyserver.domain.arena.application.CurrentArenaSettings
import team.inreok.poppyserver.domain.arena.model.ArenaPresets
import team.inreok.poppyserver.domain.arena.model.ArenaSettings
import team.inreok.poppyserver.global.response.ApiResponse
import team.inreok.poppyserver.global.security.AdminSessionCredentials

/** Client 와 Agent 가 읽어 가는 공간 설정. 비밀 값이 없어 공개 조회다. */
@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/arena-settings")
class ArenaSettingsController(
    private val service: ArenaSettingsService,
) {
    @GetMapping
    fun get(): ApiResponse<ArenaSettingsResponse> = ApiResponse.success(service.currentWithTimestamp().toResponse())

    @GetMapping("/presets")
    fun presets(): ApiResponse<List<ArenaSettingsResponse>> =
        ApiResponse.success(ArenaPresets.ALL.values.map { CurrentArenaSettings(it, null).toResponse() })
}

/** 관리자만 바꾼다(/api/v1/admin 아래는 관리자 세션 인터셉터와 CSRF 보호 대상). */
@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
@RequestMapping("/api/v1/admin/arena-settings")
class AdminArenaSettingsController(
    private val service: ArenaSettingsService,
) {
    @PutMapping
    fun update(
        @RequestAttribute(AdminSessionCredentials.SESSION_ID_ATTRIBUTE) adminSessionId: UUID,
        @RequestBody request: ArenaSettingsUpdateRequest,
    ): ApiResponse<ArenaSettingsResponse> {
        val updated = service.update(request.toUpdate(), changedBy = "admin-session:$adminSessionId")
        return ApiResponse.success(updated.toResponse())
    }

    @GetMapping("/history")
    fun history(): ApiResponse<List<ArenaSettingChangeResponse>> = ApiResponse.success(
        service.history().map {
            ArenaSettingChangeResponse(
                changedAt = it.changedAt,
                changedBy = it.changedBy,
                previous = CurrentArenaSettings(it.previous, null).toResponse(),
                next = CurrentArenaSettings(it.next, it.changedAt).toResponse(),
            )
        },
    )
}

data class ArenaSettingsUpdateRequest(
    val presetCode: String? = null,
    val widthM: Double? = null,
    val depthM: Double? = null,
    val marginM: Double? = null,
    val startXM: Double? = null,
    val startYM: Double? = null,
    val startHeading: String? = null,
) {
    fun toUpdate() = ArenaSettingsUpdate(presetCode, widthM, depthM, marginM, startXM, startYM, startHeading)
}

data class ArenaLimitsResponse(
    val usableWidthM: Double,
    val usableDepthM: Double,
    val maxMovePerCommandM: Double,
    val defaultMoveDistanceM: Double,
)

data class ArenaSettingsResponse(
    val presetCode: String?,
    val widthM: Double,
    val depthM: Double,
    val marginM: Double,
    val startXM: Double,
    val startYM: Double,
    val startHeading: String,
    val limits: ArenaLimitsResponse,
    /** 저장된 적이 없으면 null(= 기본값 6x6-박람회). */
    val updatedAt: Instant?,
)

data class ArenaSettingChangeResponse(
    val changedAt: Instant,
    val changedBy: String,
    val previous: ArenaSettingsResponse,
    val next: ArenaSettingsResponse,
)

private fun ArenaSettings.toLimits() = ArenaLimitsResponse(usableWidthM, usableDepthM, maxMovePerCommandM, defaultMoveDistanceM)

private fun CurrentArenaSettings.toResponse() = ArenaSettingsResponse(
    presetCode = settings.presetCode,
    widthM = settings.widthM,
    depthM = settings.depthM,
    marginM = settings.marginM,
    startXM = settings.startXM,
    startYM = settings.startYM,
    startHeading = settings.startHeading.code,
    limits = settings.toLimits(),
    updatedAt = updatedAt,
)
