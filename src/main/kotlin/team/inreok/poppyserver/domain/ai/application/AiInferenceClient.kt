package team.inreok.poppyserver.domain.ai.application

import tools.jackson.databind.JsonNode

interface AiInferenceClient {
    fun requestProgramDraft(utterance: String): AiInferenceDraft

    fun requestHint(request: AiHintCommand): String
}

data class AiInferenceDraft(
    val steps: JsonNode?,
    val explanation: String?,
)

data class AiHintCommand(
    val programLines: List<String>,
    val violationCode: String?,
    val violationMessage: String?,
    val violationBlockNumber: Int?,
)

class AiInferenceUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
