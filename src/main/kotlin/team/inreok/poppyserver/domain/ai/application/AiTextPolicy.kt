package team.inreok.poppyserver.domain.ai.application

import org.springframework.stereotype.Component

@Component
class AiTextPolicy {
    fun normalize(raw: String): String =
        raw.map { if (it.code <= 0x1F || it.code == 0x7F) ' ' else it }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()

    fun isInappropriate(text: String): Boolean {
        val compact = text.lowercase().replace(WHITESPACE, "")
        return BLOCKED_WORDS.any { compact.contains(it) }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val BLOCKED_WORDS = listOf(
            "시발", "씨발", "씨1발", "ㅅㅂ", "병신", "ㅂㅅ", "개새끼", "새끼", "좆", "존나", "닥쳐", "죽어", "죽여",
            "fuck", "shit", "bitch",
        )
    }
}
