package team.inreok.poppyserver.domain.session.model

import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionExperienceTest {

    @Test
    fun `MISSION으로 바꾸면 mode와 missionId를 저장하고 변경 여부를 반환한다`() {
        val session = Session.create()
        val missionId = UUID.randomUUID()

        assertTrue(session.changeExperience(ExperienceMode.MISSION, missionId))
        assertEquals(ExperienceMode.MISSION, session.mode)
        assertEquals(missionId, session.missionId)
    }

    @Test
    fun `같은 mode와 missionId면 변경하지 않는다`() {
        val session = Session.create()
        val missionId = UUID.randomUUID()
        session.changeExperience(ExperienceMode.MISSION, missionId)

        assertFalse(session.changeExperience(ExperienceMode.MISSION, missionId))
    }

    @Test
    fun `FREE로 바꾸면 missionId를 비운다`() {
        val session = Session.create()
        session.changeExperience(ExperienceMode.MISSION, UUID.randomUUID())

        assertTrue(session.changeExperience(ExperienceMode.FREE, null))
        assertEquals(ExperienceMode.FREE, session.mode)
        assertNull(session.missionId)
    }

    @Test
    fun `mode와 missionId 조합이 맞지 않으면 거부한다`() {
        val session = Session.create()

        assertFailsWith<IllegalArgumentException> { session.changeExperience(ExperienceMode.MISSION, null) }
        assertFailsWith<IllegalArgumentException> { session.changeExperience(ExperienceMode.FREE, UUID.randomUUID()) }
    }
}
