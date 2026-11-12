package team.inreok.poppyserver.domain.arena.infrastructure

import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.arena.application.ArenaSettingsChange
import team.inreok.poppyserver.domain.arena.application.ArenaSettingsRepository
import team.inreok.poppyserver.domain.arena.model.ArenaSettings
import team.inreok.poppyserver.domain.arena.model.StartHeading
import tools.jackson.databind.ObjectMapper

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class ArenaSettingsPersistenceAdapter(
    private val settingsRepository: ArenaSettingsJpaRepository,
    private val changeRepository: ArenaSettingChangeJpaRepository,
    private val objectMapper: ObjectMapper,
) : ArenaSettingsRepository {
    @Transactional(readOnly = true)
    override fun find(): Pair<ArenaSettings, Instant>? =
        settingsRepository.findById(SINGLETON_ID).orElse(null)?.let { it.toDomain() to it.updatedAt }

    @Transactional
    override fun saveWithHistory(change: ArenaSettingsChange) {
        val entity = settingsRepository.findById(SINGLETON_ID).orElseGet { ArenaSettingsEntity(id = SINGLETON_ID) }
        entity.apply(change.next, change.changedAt, change.changedBy)
        settingsRepository.save(entity)
        changeRepository.save(
            ArenaSettingChangeEntity(
                id = UUID.randomUUID(),
                changedAt = change.changedAt,
                changedBy = change.changedBy,
                previousSettings = objectMapper.writeValueAsString(change.previous.toSnapshot()),
                newSettings = objectMapper.writeValueAsString(change.next.toSnapshot()),
            ),
        )
    }

    @Transactional(readOnly = true)
    override fun history(limit: Int): List<ArenaSettingsChange> =
        changeRepository.findAllByOrderByChangedAtDesc(PageRequest.of(0, limit)).map {
            ArenaSettingsChange(
                changedAt = it.changedAt,
                changedBy = it.changedBy,
                previous = objectMapper.readValue(it.previousSettings, ArenaSnapshot::class.java).toDomain(),
                next = objectMapper.readValue(it.newSettings, ArenaSnapshot::class.java).toDomain(),
            )
        }

    private companion object {
        const val SINGLETON_ID: Short = 1
    }
}

/** 이력에 JSON 으로 남기는 값. 필드 이름은 API 와 같다. */
data class ArenaSnapshot(
    val presetCode: String? = null,
    val widthM: Double = 0.0,
    val depthM: Double = 0.0,
    val marginM: Double = 0.0,
    val startXM: Double = 0.0,
    val startYM: Double = 0.0,
    val startHeading: String = "+Y",
) {
    fun toDomain(): ArenaSettings = ArenaSettings(
        presetCode, widthM, depthM, marginM, startXM, startYM,
        requireNotNull(StartHeading.fromCode(startHeading)),
    )
}

private fun ArenaSettings.toSnapshot() = ArenaSnapshot(
    presetCode, widthM, depthM, marginM, startXM, startYM, startHeading.code,
)
