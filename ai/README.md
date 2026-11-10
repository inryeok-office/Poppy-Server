# POPPY AI (블록 초안 모델)

체험자의 한국어 문장을 블록 초안(JSON)으로 바꾸는 소형 언어모델의 데이터·학습·평가·추론 코드다.
Spring 서버(`src/`)와 분리된 Python 패키지이며 Docker 이미지에 들어가지 않는다.
AI 는 블록 초안만 만든다. 로봇 명령을 보내지 않고, 앉기(`sit`) 블록을 만들지 않는다.

## 구성

| 경로 | 내용 |
| --- | --- |
| `poppy_ai/blocks.py` | 블록 계약·LIMITS·`validate_ai_steps`·입력 정리 (Client `features/ai/model/validate.ts`, `lib/sanitizeInput.ts` 포트) |
| `poppy_ai/simulator.py` | 시뮬레이터 (Client `features/simulation/model/motionPlan.ts` 포트, 6×6 m, 여백 0.4 m, 시작 (5.6, 0.4) +Y, 총 120 s) |
| `poppy_ai/rules.py` | 규칙 기반 planner (Client `features/ai/model/planner.ts` 포트, 벤치마크 기준선) |
| `poppy_ai/datagen.py` | 문장 → 블록 JSON 합성 데이터 생성기 |
| `data/manual_test.jsonl` | 손으로 쓴 평가 문장 40개 (생성기 템플릿과 다른 말투) |
| `tests/fixtures/client_parity.json` | Client 코드를 직접 실행해 만든 parity 기대값 |

## Client parity

`tests/test_client_parity.py` 는 Client 원본 TypeScript 를 Node 로 실행해 얻은 결과(`client_parity.json`)와
Python 포트의 결과가 같은지 확인한다. 동작 계획 431건(Client 테스트 사례 + 무작위 400건), 검증 224건,
입력 정리·방향 문구, 규칙 planner 30건을 비교한다. Client 가 바뀌면 다시 만든다.

```bash
POPPY_CLIENT_DIR=~/work/Poppy-Client POPPY_CLIENT_REF=origin/main bash scripts/export_client_fixtures.sh
python -m pytest -q
```

## 데이터 생성 (#128)

```bash
python -m poppy_ai.datagen --out ~/poppy-ai/data/<날짜>/dataset-v1 -n 8000 --seed 20261110
```

- 블록 프로그램을 무작위로 뽑고 아래를 모두 통과한 것만 남긴다.
  - `validate_ai_steps` (허용 종류, LIMITS, 반복 중첩·빈 반복 금지, 구조적 12블록)
  - 시뮬레이터 통과 (안전 구역·시간·자세)
  - 실행 1회 총 이동 4.0 m 이하 (Agent `POPPY_PHYSICAL_MAX_TOTAL_MOVE_M`, Server 한도 검증과 같음)
  - 앉기 없음
- 통과한 프로그램마다 한국어 문장을 템플릿과 말 바꾸기(숫자 표기, 단위, 어미, 접속어, 호칭)로 만든다. 100자 초과·중복 문장은 버린다.
- 분할은 프로그램 해시 기준(같은 프로그램은 같은 분할)이라 학습·검증·테스트 사이에 정답 누수가 없다.
- 같은 seed 면 같은 결과가 나온다. 결과는 `summary.json` 에 수량과 거절 건수를 남긴다.

2026-11-10 생성 결과 (`seed=20261110`, `n=8000`): train 6,784 / val 586 / test 630.
무작위 후보 14,412개 중 시뮬·한도 거절 5,790, 문장 중복 600, 길이 초과 22.
