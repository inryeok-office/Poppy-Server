package team.inreok.poppyserver.domain.admin.application

import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminCredentialVerifierTest {
    private val encoder = CountingPasswordEncoder(BCryptPasswordEncoder(4))
    private val passwordHash = requireNotNull(encoder.encode(PASSWORD))

    @Test
    fun `올바른 credential은 통과한다`() {
        verifier("admin", passwordHash).verify("admin", PASSWORD)
    }

    @Test
    fun `비밀번호가 틀리면 ADMIN_CREDENTIAL_INVALID를 던진다`() {
        assertInvalid { verifier("admin", passwordHash).verify("admin", "wrong-password") }
    }

    @Test
    fun `없는 username도 더미 해시로 matches를 한 번 수행하고 같은 예외를 던진다`() {
        val verifier = verifier("admin", passwordHash)
        encoder.matchesCount = 0

        assertInvalid { verifier.verify("unknown", PASSWORD) }

        assertEquals(1, encoder.matchesCount)
    }

    @Test
    fun `username이 비어 있는 설정은 항상 실패한다`() {
        val verifier = verifier("", passwordHash)
        encoder.matchesCount = 0

        assertInvalid { verifier.verify("", PASSWORD) }

        assertEquals(1, encoder.matchesCount)
    }

    @Test
    fun `password-hash가 비어 있는 설정은 항상 실패한다`() {
        assertInvalid { verifier("admin", "").verify("admin", "") }
        assertInvalid { verifier("admin", "").verify("admin", PASSWORD) }
    }

    @Test
    fun `BCrypt 형식이 아닌 해시 설정은 예외 없이 실패한다`() {
        assertInvalid { verifier("admin", "change-me").verify("admin", PASSWORD) }
    }

    @Test
    fun `72바이트를 넘는 비밀번호도 credential 실패로 처리한다`() {
        assertInvalid { verifier("admin", passwordHash).verify("admin", "a".repeat(100)) }
    }

    private fun verifier(username: String, hash: String) = AdminCredentialVerifier(username, hash, encoder)

    private fun assertInvalid(block: () -> Unit) {
        val exception = assertFailsWith<ApplicationException> { block() }
        assertEquals(ErrorCode.ADMIN_CREDENTIAL_INVALID, exception.errorCode)
    }

    private class CountingPasswordEncoder(private val delegate: PasswordEncoder) : PasswordEncoder {
        var matchesCount = 0

        override fun encode(rawPassword: CharSequence?): String? = delegate.encode(rawPassword)

        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            matchesCount += 1
            return delegate.matches(rawPassword, encodedPassword)
        }
    }

    companion object {
        private const val PASSWORD = "unit-test-password"
    }
}
