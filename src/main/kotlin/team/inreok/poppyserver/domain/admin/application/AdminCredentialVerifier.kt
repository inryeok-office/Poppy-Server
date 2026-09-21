package team.inreok.poppyserver.domain.admin.application

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Component
class AdminCredentialVerifier(
    @Value("\${poppy.admin.username:}") private val configuredUsername: String,
    @Value("\${poppy.admin.password-hash:}") private val configuredPasswordHash: String,
    private val passwordEncoder: PasswordEncoder,
) {
    private val dummyPasswordHash: String = requireNotNull(passwordEncoder.encode(UUID.randomUUID().toString()))

    fun verify(username: String, password: String) {
        val configured = configuredUsername.isNotEmpty() && configuredPasswordHash.isNotEmpty()
        val usernameMatches = configured && sameBytes(username, configuredUsername)
        val hash = if (usernameMatches) configuredPasswordHash else dummyPasswordHash
        val passwordMatches = matches(password, hash)
        if (!usernameMatches || !passwordMatches) {
            throw ApplicationException(ErrorCode.ADMIN_CREDENTIAL_INVALID)
        }
    }

    private fun matches(password: String, hash: String): Boolean = try {
        passwordEncoder.matches(password, hash)
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun sameBytes(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.UTF_8),
        right.toByteArray(StandardCharsets.UTF_8),
    )
}
