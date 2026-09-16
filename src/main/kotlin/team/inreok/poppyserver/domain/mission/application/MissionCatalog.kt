package team.inreok.poppyserver.domain.mission.application

import java.util.UUID
import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.block.model.BlockType
import team.inreok.poppyserver.domain.mission.model.CompletionCondition
import team.inreok.poppyserver.domain.mission.model.Mission
import team.inreok.poppyserver.domain.mission.model.MissionDifficulty
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Component
class MissionCatalog {
    fun list(): List<Mission> = MISSIONS

    fun getById(missionId: UUID): Mission =
        MISSIONS.find { it.id == missionId } ?: throw ApplicationException(ErrorCode.MISSION_NOT_FOUND)

    companion object {
        private val MISSIONS = listOf(
            Mission(
                id = UUID.fromString("f3f450e4-8faf-4d2e-b2aa-6f76ac115412"),
                title = "로봇 택배 배달",
                difficulty = MissionDifficulty.EASY,
                summary = "GO2가 출발선에서 앞의 배송 지점까지 이동해 멈추면 성공하는 가장 기본적인 미션.",
                description = "GO2가 출발선에서 앞의 배송 지점까지 이동해 멈추면 성공하는 가장 기본적인 미션.",
                goal = "앞으로 이동해 지정된 도착 지점에서 정지한다.",
                completionCondition = CompletionCondition(
                    description = listOf(
                        "지정된 이동 거리 범위 안에서 전진 명령이 실행됨",
                        "마지막 명령이 정지 상태로 종료됨",
                        "시뮬레이션과 실제 실행이 동일한 블록 버전임",
                    ).joinToString("\n"),
                ),
                timeLimitSeconds = null,
                estimatedSeconds = null,
                allowedBlocks = listOf(BlockType.MOVE_FORWARD, BlockType.STOP),
            ),
            Mission(
                id = UUID.fromString("bb2062b6-6cd2-4d09-b677-7ebfaeaed381"),
                title = "뒤를 봐!",
                difficulty = MissionDifficulty.EASY,
                summary = "로봇이 정면에서 시작해 뒤쪽의 목표를 바라보도록 회전시키는 미션.",
                description = "로봇이 정면에서 시작해 뒤쪽의 목표를 바라보도록 회전시키는 미션.",
                goal = "회전 블록을 사용해 지정 방향을 바라보게 한다.",
                completionCondition = CompletionCondition(
                    description = listOf(
                        "목표 회전각 허용 오차 안에 도달",
                        "이동 없이 회전만 사용",
                        "정지 상태로 종료",
                    ).joinToString("\n"),
                ),
                timeLimitSeconds = null,
                estimatedSeconds = null,
                allowedBlocks = listOf(BlockType.TURN_LEFT, BlockType.TURN_RIGHT, BlockType.STOP),
            ),
            Mission(
                id = UUID.fromString("c7c16569-89e9-4289-9094-3503649180cc"),
                title = "인사하고 출발!",
                difficulty = MissionDifficulty.EASY,
                summary = "체험 시작 전에 로봇이 앉았다 일어난 뒤 짧게 앞으로 이동하는 미션.",
                description = "체험 시작 전에 로봇이 앉았다 일어난 뒤 짧게 앞으로 이동하는 미션.",
                goal = "자세 변화와 이동 명령을 순서대로 조합한다.",
                completionCondition = CompletionCondition(
                    description = listOf(
                        "자세 명령 순서가 올바름",
                        "일어서기 완료 후에만 이동 시작",
                        "정지 상태로 종료",
                    ).joinToString("\n"),
                ),
                timeLimitSeconds = null,
                estimatedSeconds = null,
                allowedBlocks = listOf(
                    BlockType.SIT,
                    BlockType.WAIT,
                    BlockType.STAND,
                    BlockType.MOVE_FORWARD,
                    BlockType.STOP,
                ),
            ),
            Mission(
                id = UUID.fromString("070cc084-7085-4d78-af20-fa8802feec0b"),
                title = "편의점 심부름",
                difficulty = MissionDifficulty.NORMAL,
                summary = "앞으로 이동한 뒤 코너를 돌아 목적지까지 가는 ㄱ자 경로 미션.",
                description = "앞으로 이동한 뒤 코너를 돌아 목적지까지 가는 ㄱ자 경로 미션.",
                goal = "전진과 회전을 조합해 두 구간의 경로를 완성한다.",
                completionCondition = CompletionCondition(
                    description = listOf(
                        "첫 번째 이동, 회전, 두 번째 이동 순서가 모두 충족",
                        "목표 도착 영역과 방향 허용 오차 내에 종료",
                    ).joinToString("\n"),
                ),
                timeLimitSeconds = null,
                estimatedSeconds = null,
                allowedBlocks = listOf(
                    BlockType.MOVE_FORWARD,
                    BlockType.TURN_LEFT,
                    BlockType.TURN_RIGHT,
                    BlockType.STOP,
                ),
            ),
            Mission(
                id = UUID.fromString("2a0af46c-0294-42b4-8cee-4a2f83cb3b58"),
                title = "집으로 돌아가기",
                difficulty = MissionDifficulty.NORMAL,
                summary = "출발점에서 이동한 뒤 방향을 바꾸고 다시 출발 지점 근처로 복귀하는 미션.",
                description = "출발점에서 이동한 뒤 방향을 바꾸고 다시 출발 지점 근처로 복귀하는 미션.",
                goal = "이동과 회전을 조합해 왕복 경로를 만든다.",
                completionCondition = CompletionCondition(
                    description = listOf(
                        "지정 지점까지 이동 후 반대 방향으로 전환",
                        "시작 지점의 허용 반경 안에서 종료",
                        "전체 실행 시간이 제한 범위 이내",
                    ).joinToString("\n"),
                ),
                timeLimitSeconds = null,
                estimatedSeconds = null,
                allowedBlocks = listOf(
                    BlockType.MOVE_FORWARD,
                    BlockType.TURN_LEFT,
                    BlockType.TURN_RIGHT,
                    BlockType.STOP,
                ),
            ),
            Mission(
                id = UUID.fromString("fca33818-b066-44de-be7d-8afd113e47a9"),
                title = "사각 순찰대",
                difficulty = MissionDifficulty.HARD,
                summary = "반복 블록을 이용해 네 변을 도는 사각형 순찰 경로를 만드는 미션.",
                description = "반복 블록을 이용해 네 변을 도는 사각형 순찰 경로를 만드는 미션.",
                goal = "반복 블록으로 `전진 + 회전` 패턴을 압축해 사각 경로를 완주한다.",
                completionCondition = CompletionCondition(
                    description = listOf(
                        "반복 블록 사용 필수",
                        "네 번의 이동·회전 패턴 완료",
                        "마지막 위치가 시작점 허용 반경 안에 있음",
                        "전체 회전량이 한 바퀴에 해당하는 허용 범위 안에 있음",
                    ).joinToString("\n"),
                ),
                timeLimitSeconds = null,
                estimatedSeconds = null,
                allowedBlocks = listOf(
                    BlockType.REPEAT,
                    BlockType.MOVE_FORWARD,
                    BlockType.TURN_LEFT,
                    BlockType.TURN_RIGHT,
                    BlockType.STOP,
                ),
            ),
            Mission(
                id = UUID.fromString("5fc3d1c9-b2d5-4fff-8689-ad44a32b9639"),
                title = "숫자 8 드라이브",
                difficulty = MissionDifficulty.HARD,
                summary = "두 개의 작은 순환 경로를 이어 숫자 8처럼 보이는 이동 패턴을 만드는 미션.",
                description = "두 개의 작은 순환 경로를 이어 숫자 8처럼 보이는 이동 패턴을 만드는 미션.",
                goal = "여러 이동과 좌·우 회전을 조합해 좌우 대칭에 가까운 경로를 완성한다.",
                completionCondition = CompletionCondition(
                    description = listOf(
                        "좌측 루프와 우측 루프를 모두 수행",
                        "허용된 이동 거리·회전각·전체 실행 시간 안에서 종료",
                        "코스 이탈이나 명령 제한 초과 없음",
                    ).joinToString("\n"),
                ),
                timeLimitSeconds = null,
                estimatedSeconds = null,
                allowedBlocks = listOf(
                    BlockType.MOVE_FORWARD,
                    BlockType.TURN_LEFT,
                    BlockType.TURN_RIGHT,
                    BlockType.STOP,
                ),
            ),
            Mission(
                id = UUID.fromString("dd8896ba-55e7-4eee-9606-01d788ef1e15"),
                title = "최소 블록 챌린지",
                difficulty = MissionDifficulty.HARD,
                summary = "정해진 코스를 가장 적은 블록 수로 완성하는 퍼즐형 미션.",
                description = "정해진 코스를 가장 적은 블록 수로 완성하는 퍼즐형 미션.",
                goal = "동일한 목표 경로를 제한된 블록 수 안에서 완성한다.",
                completionCondition = CompletionCondition(
                    description = listOf(
                        "목표 위치와 방향 도달",
                        "최대 블록 수 이하",
                        "시뮬레이션 검증 통과",
                        "안전 제한값 초과 없음",
                    ).joinToString("\n"),
                ),
                timeLimitSeconds = null,
                estimatedSeconds = null,
                allowedBlocks = listOf(
                    BlockType.REPEAT,
                    BlockType.MOVE_FORWARD,
                    BlockType.TURN_LEFT,
                    BlockType.TURN_RIGHT,
                    BlockType.STOP,
                ),
            ),
        )
    }
}
