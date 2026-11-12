package team.inreok.poppyserver.domain.arena.application

import java.time.Instant
import team.inreok.poppyserver.domain.arena.model.ArenaSettings

data class ArenaSettingsChange(
    val changedAt: Instant,
    val changedBy: String,
    val previous: ArenaSettings,
    val next: ArenaSettings,
)

interface ArenaSettingsRepository {
    fun find(): Pair<ArenaSettings, Instant>?

    /** 설정을 저장하고 변경 이력을 같은 트랜잭션에 남긴다. */
    fun saveWithHistory(change: ArenaSettingsChange)

    fun history(limit: Int): List<ArenaSettingsChange>
}
