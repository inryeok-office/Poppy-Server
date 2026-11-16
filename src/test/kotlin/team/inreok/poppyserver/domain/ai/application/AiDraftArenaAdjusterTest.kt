package team.inreok.poppyserver.domain.ai.application

import org.junit.jupiter.api.Test
import team.inreok.poppyserver.domain.arena.application.ArenaSettingsProvider
import team.inreok.poppyserver.domain.arena.model.ArenaPresets
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiDraftArenaAdjusterTest {
    private val booth = AiDraftArenaAdjuster(ArenaSettingsProvider { ArenaPresets.ALL.getValue(ArenaPresets.BOOTH_3X3) })
    private val expo = AiDraftArenaAdjuster()

    private fun fwd(m: Number) = mapOf<String, Any>("kind" to "moveForward", "distanceM" to m)
    private fun back(m: Number) = mapOf<String, Any>("kind" to "move", "distanceM" to m)
    private val left = mapOf<String, Any>("kind" to "rotateLeft", "degrees" to 90L)
    private fun repeat(count: Long, vararg body: Map<String, Any>) =
        mapOf<String, Any>("kind" to "repeat", "count" to count, "body" to body.toList())

    @Test
    fun `6x6 기본 공간에서는 아무것도 바꾸지 않는다`() {
        val steps = listOf(fwd(2L), repeat(4, fwd(1L), left))
        val result = expo.adjust("앞으로 가고 네모 모양으로 걸어", steps)
        assertEquals(steps, result.steps)
        assertNull(result.note)
    }

    @Test
    fun `3x3 에서 거리를 말하지 않으면 모든 이동이 0_5m 가 된다 - 네모는 0_5m x4`() {
        val result = booth.adjust("네모 모양으로 걸어", listOf(repeat(4, fwd(1L), left)))
        assertEquals(listOf(repeat(4, fwd(0.5), left)), result.steps)
        assertEquals("거리를 말하지 않아서 이 공간에 맞게 0.5m 씩 가도록 만들었어요.", result.note)

        assertEquals(listOf(fwd(0.5)), booth.adjust("앞으로 가", listOf(fwd(2L))).steps)
        assertEquals(listOf(back(0.5)), booth.adjust("뒤로 가", listOf(back(1L))).steps)
    }

    @Test
    fun `3x3 에서 거리를 말했으면 1_2m 를 넘는 값만 줄인다`() {
        val kept = booth.adjust("앞으로 1미터 가", listOf(fwd(1L)))
        assertEquals(listOf(fwd(1L)), kept.steps)
        assertNull(kept.note)

        val clamped = booth.adjust("앞으로 2미터 가", listOf(fwd(2L)))
        assertEquals(listOf(fwd(1.2)), clamped.steps)
        assertEquals("이 공간에서는 한 번에 1.2m 까지 갈 수 있어서 줄였어요.", clamped.note)

        assertEquals(listOf(fwd(0.5)), booth.adjust("앞으로 50cm 가", listOf(fwd(0.5))).steps)
        assertEquals(listOf(fwd(0.5)), booth.adjust("반 미터 앞으로", listOf(fwd(0.5))).steps)
        assertEquals(listOf(fwd(1.2)), booth.adjust("앞으로 3 m 가", listOf(fwd(3L))).steps)
    }

    @Test
    fun `이동이 없는 초안은 그대로다`() {
        val steps = listOf(mapOf<String, Any>("kind" to "greet"), left)
        val result = booth.adjust("인사하고 왼쪽으로 돌아", steps)
        assertEquals(steps, result.steps)
        assertNull(result.note)
    }
}
