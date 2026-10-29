package team.inreok.poppyserver.domain.mission.application

import org.junit.jupiter.api.Test
import team.inreok.poppyserver.domain.block.application.BlockDefinitionCatalog
import team.inreok.poppyserver.domain.block.model.BlockInstance
import team.inreok.poppyserver.domain.block.model.BlockParameters
import team.inreok.poppyserver.domain.block.model.BlockProgram
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
    fun `catalog는 Notion 미션 9종을 원본 순서대로 포함한다`() {
        assertEquals(
            listOf(
                "로봇 택배 배달",
                "뒤를 봐!",
                "인사하고 출발!",
                "편의점 심부름",
                "무대 중앙에서 포즈",
                "집으로 돌아가기",
                "사각 순찰대",
                "숫자 8 드라이브",
                "최소 블록 챌린지",
            ),
            missions.map { it.title },
        )
    }

    @Test
    fun `무대 중앙에서 포즈는 이동·회전·정지·대기·특수 동작을 허용한다`() {
        val mission = missions.single { it.title == "무대 중앙에서 포즈" }

        assertEquals(
            listOf(
                BlockType.MOVE_FORWARD,
                BlockType.TURN_LEFT,
                BlockType.TURN_RIGHT,
                BlockType.STOP,
                BlockType.WAIT,
                BlockType.PRESET,
            ),
            mission.allowedBlocks,
        )
        assertEquals(team.inreok.poppyserver.domain.mission.model.MissionDifficulty.NORMAL, mission.difficulty)
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

    @Test
    fun `완료 규칙이 요구하는 블록은 모두 allowedBlocks 안에 있다`() {
        missions.forEach { mission ->
            val rule = mission.completionRule
            val referenced = rule.requiredBlocks.flatten() +
                rule.requiredOrder.flatten() +
                rule.minimumExecutions.flatMap { it.types } +
                listOfNotNull(rule.lastAction)
            assertTrue(
                mission.allowedBlocks.containsAll(referenced),
                "${mission.title} rule references blocks outside allowedBlocks",
            )
        }
    }

    @Test
    fun `미션마다 allowedBlocks 만으로 완료 규칙을 통과하는 예시가 있다`() {
        val examples = mapOf(
            "로봇 택배 배달" to listOf(b(BlockType.MOVE_FORWARD), b(BlockType.STOP)),
            "뒤를 봐!" to listOf(b(BlockType.TURN_LEFT), b(BlockType.STOP)),
            "인사하고 출발!" to listOf(b(BlockType.SIT), b(BlockType.WAIT), b(BlockType.STAND), b(BlockType.MOVE_FORWARD), b(BlockType.STOP)),
            "편의점 심부름" to listOf(b(BlockType.MOVE_FORWARD), b(BlockType.TURN_RIGHT), b(BlockType.MOVE_FORWARD), b(BlockType.STOP)),
            "무대 중앙에서 포즈" to listOf(b(BlockType.MOVE_FORWARD), b(BlockType.TURN_LEFT), b(BlockType.MOVE_FORWARD), b(BlockType.STOP), b(BlockType.PRESET), b(BlockType.WAIT)),
            "집으로 돌아가기" to listOf(b(BlockType.MOVE_FORWARD), b(BlockType.TURN_LEFT), b(BlockType.TURN_LEFT), b(BlockType.MOVE_FORWARD), b(BlockType.STOP)),
            "사각 순찰대" to listOf(repeat(4, b(BlockType.MOVE_FORWARD), b(BlockType.TURN_RIGHT)), b(BlockType.STOP)),
            "숫자 8 드라이브" to listOf(repeat(4, b(BlockType.MOVE_FORWARD), b(BlockType.TURN_LEFT)), repeat(4, b(BlockType.MOVE_FORWARD), b(BlockType.TURN_RIGHT)), b(BlockType.STOP)),
            "최소 블록 챌린지" to listOf(repeat(2, b(BlockType.MOVE_FORWARD), b(BlockType.TURN_LEFT)), b(BlockType.STOP)),
        )
        assertEquals(missions.map { it.title }.toSet(), examples.keys)

        missions.forEach { mission ->
            val blocks = examples.getValue(mission.title)
            val result = mission.completionRule.evaluate(BlockProgram(1, listOf(b(BlockType.START)) + blocks + b(BlockType.END)))
            assertTrue(result.passed, "${mission.title}: ${result.reasons}")
        }
    }

    @Test
    fun `무대 중앙에서 포즈는 정지 전에 특수 동작을 하면 실패한다`() {
        val mission = missions.single { it.title == "무대 중앙에서 포즈" }

        val result = mission.completionRule.evaluate(
            BlockProgram(1, listOf(b(BlockType.START), b(BlockType.MOVE_FORWARD), b(BlockType.PRESET), b(BlockType.STOP), b(BlockType.END))),
        )

        assertFalse(result.passed)
    }

    private fun b(type: BlockType): BlockInstance = BlockInstance(id = type.name, type = type, parameters = BlockParameters.None)

    private fun repeat(count: Long, vararg children: BlockInstance): BlockInstance =
        BlockInstance(id = "repeat", type = BlockType.REPEAT, parameters = BlockParameters.Count(count), children = children.toList())
}
