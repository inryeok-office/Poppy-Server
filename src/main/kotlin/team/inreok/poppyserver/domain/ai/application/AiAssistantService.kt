package team.inreok.poppyserver.domain.ai.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
class AiAssistantService(
    private val inferenceClient: AiInferenceClient,
    private val stepValidator: AiDraftStepValidator,
    private val textPolicy: AiTextPolicy,
) {
    fun draftProgram(rawUtterance: String): AiProgramDraftResult {
        val utterance = textPolicy.normalize(rawUtterance)
        if (utterance.isEmpty() || utterance.length > MAX_UTTERANCE_LENGTH) {
            throw ApplicationException(ErrorCode.AI_INPUT_INVALID)
        }
        if (textPolicy.isInappropriate(utterance)) {
            throw ApplicationException(ErrorCode.AI_INPUT_REJECTED)
        }
        val draft = callInference { inferenceClient.requestProgramDraft(utterance) }
        val steps = try {
            stepValidator.validate(draft.steps)
        } catch (exception: InvalidAiDraftException) {
            log.warn("AI draft rejected by contract validation: {}", exception.message)
            throw ApplicationException(ErrorCode.AI_UNAVAILABLE)
        }
        return AiProgramDraftResult(steps = steps, explanation = safeExplanation(draft.explanation))
    }

    fun hint(command: AiHintCommand): String {
        if (command.programLines.any { it.length > MAX_UTTERANCE_LENGTH }) {
            throw ApplicationException(ErrorCode.AI_INPUT_INVALID)
        }
        val message = callInference { inferenceClient.requestHint(command) }.let(textPolicy::normalize)
        if (message.isEmpty() || message.length > MAX_TEXT_LENGTH || textPolicy.isInappropriate(message)) {
            throw ApplicationException(ErrorCode.AI_UNAVAILABLE)
        }
        return message
    }

    private fun safeExplanation(raw: String?): String {
        val text = raw?.let(textPolicy::normalize).orEmpty()
        if (text.isEmpty() || text.length > MAX_TEXT_LENGTH || textPolicy.isInappropriate(text)) {
            return DEFAULT_EXPLANATION
        }
        return text
    }

    private fun <T> callInference(call: () -> T): T = try {
        call()
    } catch (exception: AiInferenceUnavailableException) {
        log.warn("AI inference unavailable: {}", exception.message)
        throw ApplicationException(ErrorCode.AI_UNAVAILABLE)
    }

    private companion object {
        const val MAX_UTTERANCE_LENGTH = 100
        const val MAX_TEXT_LENGTH = 200
        const val DEFAULT_EXPLANATION = "말한 내용으로 블록 초안을 만들었어요."
        val log = LoggerFactory.getLogger(AiAssistantService::class.java)
    }
}

data class AiProgramDraftResult(
    val steps: List<Map<String, Any>>,
    val explanation: String,
)
