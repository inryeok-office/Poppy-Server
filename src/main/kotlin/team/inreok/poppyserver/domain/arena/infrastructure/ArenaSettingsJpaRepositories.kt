package team.inreok.poppyserver.domain.arena.infrastructure

import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ArenaSettingsJpaRepository : JpaRepository<ArenaSettingsEntity, Short>

interface ArenaSettingChangeJpaRepository : JpaRepository<ArenaSettingChangeEntity, UUID> {
    fun findAllByOrderByChangedAtDesc(pageable: Pageable): List<ArenaSettingChangeEntity>
}
