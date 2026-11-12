package team.inreok.poppyserver.domain.arena.application

import team.inreok.poppyserver.domain.arena.model.ArenaPresets
import team.inreok.poppyserver.domain.arena.model.ArenaSettings

/** 현재 체험 공간. DB 가 없는 구성(테스트 등)에서는 기본값(6×6)을 쓴다. */
fun interface ArenaSettingsProvider {
    fun current(): ArenaSettings
}

object DefaultArenaSettingsProvider : ArenaSettingsProvider {
    override fun current(): ArenaSettings = ArenaPresets.DEFAULT
}
