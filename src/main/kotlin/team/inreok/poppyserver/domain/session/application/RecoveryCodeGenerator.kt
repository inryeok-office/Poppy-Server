package team.inreok.poppyserver.domain.session.application

import java.security.SecureRandom

object RecoveryCodeGenerator {
    private const val CODE_LENGTH = 8
    private const val GROUP_LENGTH = 4
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val secureRandom = SecureRandom()

    fun issue(): IssuedRecoveryCode {
        val chars = CharArray(CODE_LENGTH) { ALPHABET[secureRandom.nextInt(ALPHABET.length)] }
        val raw = String(chars).chunked(GROUP_LENGTH).joinToString("-")
        return IssuedRecoveryCode(raw = raw, digest = digest(raw))
    }

    fun isValidFormat(raw: String): Boolean =
        raw.length == CODE_LENGTH + 1 && raw[GROUP_LENGTH] == '-' &&
            raw.filterIndexed { index, _ -> index != GROUP_LENGTH }.all { it in ALPHABET }

    fun normalize(raw: String): String = raw.uppercase()

    fun digest(raw: String): String = SessionAccessVerifier.digest(raw)
}

data class IssuedRecoveryCode(
    val raw: String,
    val digest: String,
)
