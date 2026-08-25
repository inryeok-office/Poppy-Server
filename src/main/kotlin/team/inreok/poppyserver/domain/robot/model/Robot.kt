package team.inreok.poppyserver.domain.robot.model

import java.time.Instant
import java.util.UUID

class Robot private constructor(
    val id: UUID,
    aliasValue: String,
    modelValue: String,
    editionValue: String,
    firmwareVersion: String?,
    sdkVersion: String?,
    val agentId: UUID?,
    safetyProfileId: UUID?,
    val isExternal: Boolean,
    currentExecutionIdValue: UUID?,
    lastHeartbeatAtValue: Instant?,
    connectionStatusValue: RobotConnectionStatus,
    operationStatusValue: RobotOperationStatus,
    initialCapabilities: Collection<RobotCapability>,
) {

    var alias: String = requireNotBlank(aliasValue, "alias")
        private set

    var model: String = requireNotBlank(modelValue, "model")
        private set

    var edition: String = requireNotBlank(editionValue, "edition")
        private set

    var firmwareVersion: String? = firmwareVersion
        private set

    var sdkVersion: String? = sdkVersion
        private set

    var safetyProfileId: UUID? = safetyProfileId
        private set

    var connectionStatus: RobotConnectionStatus = connectionStatusValue
        private set

    var operationStatus: RobotOperationStatus = operationStatusValue
        private set

    var active: Boolean = true
        private set

    var lastHeartbeatAt: Instant? = lastHeartbeatAtValue
        private set

    var currentExecutionId: UUID? = currentExecutionIdValue
        private set

    val occupied: Boolean
        get() = currentExecutionId != null

    private val mutableCapabilities: MutableMap<String, RobotCapability> = mutableMapOf()

    val capabilities: Map<String, RobotCapability>
        get() = mutableCapabilities.toMap()

    init {
        initialCapabilities.forEach { capability ->
            reportCapability(capability.code, capability.status)
        }
    }

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

    fun update(
        alias: String?,
        firmwareVersion: String?,
        sdkVersion: String?,
        capabilities: Collection<RobotCapability>,
        safetyProfileId: UUID?,
        operationStatus: RobotOperationStatus?,
    ) {
        alias?.let { aliasValue -> this.alias = requireNotBlank(aliasValue, "alias") }
        firmwareVersion?.let { version -> this.firmwareVersion = requireNotBlank(version, "firmwareVersion") }
        sdkVersion?.let { version -> this.sdkVersion = requireNotBlank(version, "sdkVersion") }
        safetyProfileId?.let { this.safetyProfileId = it }
        operationStatus?.let { this.operationStatus = it }
        replaceCapabilities(capabilities)
    }

    fun replaceCapabilities(capabilities: Collection<RobotCapability>) {
        mutableCapabilities.clear()
        capabilities.forEach { capability ->
            reportCapability(capability.code, capability.status)
        }
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
            edition: String = model,
            agentId: UUID? = null,
            safetyProfileId: UUID? = null,
            isExternal: Boolean = false,
            capabilities: Collection<RobotCapability> = emptyList(),
        ): Robot = Robot(
            id = UUID.randomUUID(),
            aliasValue = alias,
            modelValue = model,
            editionValue = edition,
            firmwareVersion = firmwareVersion,
            sdkVersion = sdkVersion,
            agentId = agentId,
            safetyProfileId = safetyProfileId,
            isExternal = isExternal,
            currentExecutionIdValue = null,
            lastHeartbeatAtValue = null,
            connectionStatusValue = RobotConnectionStatus.OFFLINE,
            operationStatusValue = RobotOperationStatus.UNAVAILABLE,
            initialCapabilities = capabilities,
        )

        fun restore(
            id: UUID,
            alias: String,
            model: String,
            edition: String,
            firmwareVersion: String?,
            sdkVersion: String?,
            agentId: UUID?,
            safetyProfileId: UUID?,
            isExternal: Boolean,
            currentExecutionId: UUID?,
            lastHeartbeatAt: Instant?,
            connectionStatus: RobotConnectionStatus,
            operationStatus: RobotOperationStatus,
            capabilities: Collection<RobotCapability>,
        ): Robot = Robot(
            id = id,
            aliasValue = alias,
            modelValue = model,
            editionValue = edition,
            firmwareVersion = firmwareVersion,
            sdkVersion = sdkVersion,
            agentId = agentId,
            safetyProfileId = safetyProfileId,
            isExternal = isExternal,
            currentExecutionIdValue = currentExecutionId,
            lastHeartbeatAtValue = lastHeartbeatAt,
            connectionStatusValue = connectionStatus,
            operationStatusValue = operationStatus,
            initialCapabilities = capabilities,
        )

        private fun requireNotBlank(value: String, field: String): String {
            require(value.isNotBlank()) { "$field 는 비어 있을 수 없습니다" }
            return value
        }
    }
}
