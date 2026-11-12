package team.inreok.poppyserver.domain.arena.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArenaSettingsTest {
    private val expo = ArenaPresets.ALL.getValue(ArenaPresets.EXPO_6X6)
    private val booth = ArenaPresets.ALL.getValue(ArenaPresets.BOOTH_3X3)

    @Test
    fun `기본값은 지금까지의 6x6 동작과 같다`() {
        assertEquals(expo, ArenaPresets.DEFAULT)
        assertEquals(6.0, expo.widthM)
        assertEquals(6.0, expo.depthM)
        assertEquals(0.4, expo.marginM)
        assertEquals(5.6, expo.startXM)
        assertEquals(0.4, expo.startYM)
        assertEquals(5.2, expo.usableWidthM)
        assertEquals(3.0, expo.maxMovePerCommandM)
        assertEquals(1.0, expo.defaultMoveDistanceM)
        assertTrue(expo.violations().isEmpty())
    }

    @Test
    fun `3x3 부스는 가용 2_0m 1회 이동 1_2m 기본 이동 0_5m 로 계산된다`() {
        assertEquals(0.5, booth.marginM)
        assertEquals(2.0, booth.usableWidthM)
        assertEquals(2.0, booth.usableDepthM)
        assertEquals(1.2, booth.maxMovePerCommandM)
        assertEquals(0.5, booth.defaultMoveDistanceM)
        assertEquals(2.5, booth.startXM)
        assertEquals(0.5, booth.startYM)
        assertTrue(booth.violations().isEmpty())
    }

    @Test
    fun `1회 이동 한도는 가용 영역에서 회전 지름 0_8m 를 뺀 값이고 3m 를 넘지 않는다`() {
        assertEquals(2.2, custom(width = 4.0, depth = 4.0, margin = 0.5, startX = 3.5, startY = 0.5).maxMovePerCommandM)
        assertEquals(3.0, custom(width = 6.0, depth = 6.0, margin = 0.4, startX = 5.6, startY = 0.4).maxMovePerCommandM)
        // 짧은 변이 기준이다
        assertEquals(1.2, custom(width = 5.0, depth = 3.0, margin = 0.5, startX = 4.5, startY = 0.5).maxMovePerCommandM)
    }

    @Test
    fun `하한 상한과 출발점 검증`() {
        assertTrue("widthM" in custom(width = 6.5).violations())
        assertTrue("depthM" in custom(depth = 1.9).violations())
        assertTrue("marginM" in custom(margin = 0.3).violations(), "Agent 하드 최소 벽 여유 0.4 m 보다 작으면 거부")
        assertTrue("marginM" in custom(margin = 1.1).violations())
        assertTrue("startXM" in custom(startX = 0.3).violations())
        assertTrue("startXM" in custom(startX = 5.7).violations())
        assertTrue("startYM" in custom(startY = 6.0).violations())
        assertTrue("widthM" in custom(width = 2.0, margin = 0.4, startX = 1.0, startY = 1.0).violations(), "가용 폭 1.2 m < 1.3 m")
        assertTrue(custom(width = Double.NaN).violations().isNotEmpty())
    }

    private fun custom(
        width: Double = 6.0,
        depth: Double = 6.0,
        margin: Double = 0.4,
        startX: Double = 5.6,
        startY: Double = 0.4,
    ) = ArenaSettings(null, width, depth, margin, startX, startY, StartHeading.PLUS_Y)
}
