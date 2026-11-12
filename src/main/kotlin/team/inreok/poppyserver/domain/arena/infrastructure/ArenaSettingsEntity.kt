package team.inreok.poppyserver.domain.arena.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import team.inreok.poppyserver.domain.arena.model.ArenaSettings
import team.inreok.poppyserver.domain.arena.model.StartHeading

@Entity
@Table(name = "experience_arena_settings")
class ArenaSettingsEntity(
    @Id
    var id: Short = 1,
    @Column(name = "preset_code")
    var presetCode: String? = null,
    @Column(name = "width_m", nullable = false)
    var widthM: Double = 0.0,
    @Column(name = "depth_m", nullable = false)
    var depthM: Double = 0.0,
    @Column(name = "margin_m", nullable = false)
    var marginM: Double = 0.0,
    @Column(name = "start_x_m", nullable = false)
    var startXM: Double = 0.0,
    @Column(name = "start_y_m", nullable = false)
    var startYM: Double = 0.0,
    @Column(name = "start_heading", nullable = false)
    var startHeading: String = "+Y",
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Column(name = "updated_by", nullable = false)
    var updatedBy: String = "",
) {
    fun toDomain(): ArenaSettings = ArenaSettings(
        presetCode = presetCode,
        widthM = widthM,
        depthM = depthM,
        marginM = marginM,
        startXM = startXM,
        startYM = startYM,
        startHeading = requireNotNull(StartHeading.fromCode(startHeading)) { "unknown start heading" },
    )

    fun apply(settings: ArenaSettings, at: Instant, by: String) {
        presetCode = settings.presetCode
        widthM = settings.widthM
        depthM = settings.depthM
        marginM = settings.marginM
        startXM = settings.startXM
        startYM = settings.startYM
        startHeading = settings.startHeading.code
        updatedAt = at
        updatedBy = by
    }
}

@Entity
@Table(name = "experience_arena_setting_changes")
class ArenaSettingChangeEntity(
    @Id
    var id: UUID? = null,
    @Column(name = "changed_at", nullable = false)
    var changedAt: Instant = Instant.EPOCH,
    @Column(name = "changed_by", nullable = false)
    var changedBy: String = "",
    @Column(name = "previous_settings", nullable = false)
    var previousSettings: String = "",
    @Column(name = "new_settings", nullable = false)
    var newSettings: String = "",
)
