package team.inreok.poppyserver.domain.mission.application

import org.junit.jupiter.api.Test
import team.inreok.poppyserver.domain.block.application.BlockDefinitionCatalog
import team.inreok.poppyserver.domain.block.model.BlockType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MissionCatalogTest {
    private val missions = MissionCatalog().list()
    private val availableBlockTypes = BlockDefinitionCatalog().list()
        .filter { it.available }
        .map { it.type }
        .toSet()

    @Test
    fun `catalog는 5번을 제외한 8개 미션을 원본 순서대로 포함한다`() {
        assertEquals(
            listOf(
                "로봇 택배 배달",
                "뒤를 봐!",
                "인사하고 출발!",
                "편의점 심부름",
                "집으로 돌아가기",
                "사각 순찰대",
                "숫자 8 드라이브",
                "최소 블록 챌린지",
            ),
            missions.map { it.title },
        )
    }

    @Test
    fun `missionId는 모두 유일하다`() {
        assertEquals(missions.size, missions.map { it.id }.toSet().size)
    }

    @Test
    fun `필수 텍스트 필드는 공백이 아니다`() {
        missions.forEach { mission ->
            assertFalse(mission.title.isBlank())
            assertFalse(mission.summary.isBlank())
            assertFalse(mission.goal.isBlank())
            assertFalse(mission.completionCondition.description.isBlank())
        }
    }

    @Test
    fun `allowedBlocks는 비어있지 않고 START, END를 포함하지 않는다`() {
        missions.forEach { mission ->
            assertTrue(mission.allowedBlocks.isNotEmpty())
            assertFalse(mission.allowedBlocks.contains(BlockType.START))
            assertFalse(mission.allowedBlocks.contains(BlockType.END))
        }
    }

    @Test
    fun `allowedBlocks의 모든 BlockType은 BlockDefinitionCatalog에서 available 하다`() {
        missions.forEach { mission ->
            mission.allowedBlocks.forEach { blockType ->
                assertTrue(
                    availableBlockTypes.contains(blockType),
                    "${mission.title}의 $blockType 은 BlockDefinitionCatalog에서 available=true가 아니다",
                )
            }
        }
    }
}
