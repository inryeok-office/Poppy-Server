package team.inreok.poppyserver.domain.robot.model

import java.time.Instant
import java.util.UUID

class Robot private constructor(
    val id: UUID,
    aliasValue: String,
    modelValue: String,
    firmwareVersion: String?,
    sdkVersion: String?,
) {

    var alias: String = requireNotBlank(aliasValue, "alias")
        private set

    var model: String = requireNotBlank(modelValue, "model")
        private set

    var firmwareVersion: String? = firmwareVersion
        private set

    var sdkVersion: String? = sdkVersion
        private set

    var connectionStatus: RobotConnectionStatus = RobotConnectionStatus.OFFLINE
        private set

    var operationStatus: RobotOperationStatus = RobotOperationStatus.UNAVAILABLE
        private set

    var active: Boolean = true
        private set

    var lastHeartbeatAt: Instant? = null
        private set

    private val mutableCapabilities: MutableMap<String, RobotCapability> = mutableMapOf()

    val capabilities: Map<String, RobotCapability>
        get() = mutableCapabilities.toMap()

    fun recordHeartbeat(at: Instant) {
        check(active) { "비활성화된 Robot은 heartbeat를 기록할 수 없습니다" }
        lastHeartbeatAt = at
        connectionStatus = RobotConnectionStatus.ONLINE
    }

    fun markOffline() {
        connectionStatus = RobotConnectionStatus.OFFLINE
    }

    fun markReady() {
        check(active) { "비활성화된 Robot은 준비 상태로 전환할 수 없습니다" }
        operationStatus = RobotOperationStatus.READY
    }

    fun markUnavailable() {
        operationStatus = RobotOperationStatus.UNAVAILABLE
    }

    fun activate() {
        active = true
    }

    fun deactivate() {
        active = false
        operationStatus = RobotOperationStatus.UNAVAILABLE
    }

    fun reportCapability(code: String, status: CapabilitySupportStatus = CapabilitySupportStatus.UNVERIFIED) {
        val normalized = requireNotBlank(code, "capability code").trim().uppercase()
        mutableCapabilities[normalized] = RobotCapability(normalized, status)
    }

    companion object {
        fun register(
            alias: String,
            model: String,
            firmwareVersion: String? = null,
            sdkVersion: String? = null,
        ): Robot = Robot(UUID.randomUUID(), alias, model, firmwareVersion, sdkVersion)

        private fun requireNotBlank(value: String, field: String): String {
            require(value.isNotBlank()) { "$field 는 비어 있을 수 없습니다" }
            return value
        }
    }
}
