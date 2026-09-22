package team.inreok.poppyserver.domain.admin.presentation

import java.time.Duration
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminSessionCookieFactoryTest {
    @Test
    fun `Strict는 정상적으로 정규화된다`() {
        val cookie = AdminSessionCookieFactory("Strict", true).issue("token", Duration.ofHours(1))

        assertEquals("Strict", cookie.sameSite)
    }

    @Test
    fun `소문자 lax도 표준 표기로 정규화된다`() {
        val cookie = AdminSessionCookieFactory("lax", true).issue("token", Duration.ofHours(1))

        assertEquals("Lax", cookie.sameSite)
    }

    @Test
    fun `None은 애플리케이션 기동을 실패시킨다`() {
        assertFailsWith<IllegalStateException> { AdminSessionCookieFactory("None", true) }
    }

    @Test
    fun `알 수 없는 값은 애플리케이션 기동을 실패시킨다`() {
        assertFailsWith<IllegalStateException> { AdminSessionCookieFactory("Invalid", true) }
    }
}
