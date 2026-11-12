package team.inreok.poppyserver.domain.arena.application

import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import team.inreok.poppyserver.domain.arena.model.ArenaPresets
import team.inreok.poppyserver.domain.arena.model.ArenaSettings
import team.inreok.poppyserver.domain.arena.model.StartHeading
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.global.response.FieldErrorItem

data class ArenaSettingsUpdate(
    val presetCode: String? = null,
    val widthM: Double? = null,
    val depthM: Double? = null,
    val marginM: Double? = null,
    val startXM: Double? = null,
    val startYM: Double? = null,
    val startHeading: String? = null,
)

data class CurrentArenaSettings(val settings: ArenaSettings, val updatedAt: Instant?)

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ArenaSettingsService(
    private val repository: ArenaSettingsRepository,
) : ArenaSettingsProvider {
    override fun current(): ArenaSettings = repository.find()?.first ?: ArenaPresets.DEFAULT

    fun currentWithTimestamp(): CurrentArenaSettings {
        val stored = repository.find()
        return CurrentArenaSettings(stored?.first ?: ArenaPresets.DEFAULT, stored?.second)
    }

    fun update(request: ArenaSettingsUpdate, changedBy: String, now: Instant = Instant.now()): CurrentArenaSettings {
        val next = resolve(request)
        val violations = next.violations()
        if (violations.isNotEmpty()) {
            throw ApplicationException(
                ErrorCode.ARENA_SETTINGS_INVALID,
                fieldErrors = violations.map { FieldErrorItem(it.key, it.value) },
            )
        }
        val previous = current()
        repository.saveWithHistory(ArenaSettingsChange(now, changedBy, previous, next))
        return CurrentArenaSettings(next, now)
    }

    fun history(limit: Int = 50): List<ArenaSettingsChange> = repository.history(limit.coerceIn(1, 200))

    private fun resolve(request: ArenaSettingsUpdate): ArenaSettings {
        val custom = listOf(
            request.widthM, request.depthM, request.marginM, request.startXM, request.startYM, request.startHeading,
        )
        if (request.presetCode != null) {
            if (custom.any { it != null }) {
                throw invalid("presetCode", "preset 과 개별 값을 함께 보낼 수 없습니다")
            }
            return ArenaPresets.ALL[request.presetCode]
                ?: throw invalid("presetCode", "must be one of ${ArenaPresets.ALL.keys}")
        }
        val missing = listOf(
            "widthM" to request.widthM, "depthM" to request.depthM, "marginM" to request.marginM,
            "startXM" to request.startXM, "startYM" to request.startYM, "startHeading" to request.startHeading,
        ).filter { it.second == null }.map { it.first }
        if (missing.isNotEmpty()) {
            throw ApplicationException(
                ErrorCode.ARENA_SETTINGS_INVALID,
                fieldErrors = missing.map { FieldErrorItem(it, "is required") },
            )
        }
        val heading = StartHeading.fromCode(requireNotNull(request.startHeading))
            ?: throw invalid("startHeading", "must be one of ${StartHeading.entries.map { it.code }}")
        return ArenaSettings(
            presetCode = null,
            widthM = requireNotNull(request.widthM),
            depthM = requireNotNull(request.depthM),
            marginM = requireNotNull(request.marginM),
            startXM = requireNotNull(request.startXM),
            startYM = requireNotNull(request.startYM),
            startHeading = heading,
        )
    }

    private fun invalid(field: String, reason: String) =
        ApplicationException(ErrorCode.ARENA_SETTINGS_INVALID, fieldErrors = listOf(FieldErrorItem(field, reason)))
}
