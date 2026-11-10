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

## 학습 (#129)

- 모델: `Qwen/Qwen2.5-1.5B-Instruct` (Apache-2.0, 모델 카드 `license: apache-2.0` 확인). 비교 후보 `Qwen/Qwen3-1.7B`(Apache-2.0)는 다운로드했으나 사용량 제한으로 학습·평가하지 못함(미측정).
- QLoRA: 4-bit NF4 + double quant, LoRA r=16/alpha=32, q/k/v/o/gate/up/down, lr 2e-4 cosine, batch 4×accum 4, 2 epoch, bf16, paged AdamW 8bit, GPU 90°C 이상이면 멈추는 콜백.
- 결과(2026-11-10, RTX 3060 Laptop 6GB): 학습 64.6분(848 step), train loss 0.0155, val loss 0.0035, 최대 GPU 84°C, 피크 메모리 5.09 GB.

```bash
python -m poppy_ai.train --base Qwen/Qwen2.5-1.5B-Instruct --data ~/poppy-ai/data/<날짜>/dataset-v1 \
  --out ~/poppy-ai/models/<날짜>/qwen25-1.5b-lora --epochs 2 --batch 4 --grad-accum 4
```

## 양자화·추론 (#130)

- `python -m poppy_ai.export_gguf --base Qwen/Qwen2.5-1.5B-Instruct --adapter <adapter> --out <dir> --name qwen25-1.5b-poppy` → LoRA 병합 → llama.cpp `convert_hf_to_gguf.py` f16 → Q4_K_M (약 0.99 GB).
- `poppy_ai/grammar.py` GBNF 로 블록 JSON 형식을 강제하고, 결과를 `validate_ai_steps` 로 다시 검증한다.
- 추론 서비스: `POPPY_AI_MODEL_PATH=<gguf> uvicorn poppy_ai.server:app --host 127.0.0.1 --port 8090` (`/health`, `/v1/program-drafts`, `/v1/hints`). Server 는 `POPPY_AI_INFERENCE_URL=http://127.0.0.1:8090`. 로봇·Agent 와 통신하지 않는다.
- llama-cpp-python CUDA 빌드는 `LD_LIBRARY_PATH` 에 venv 의 `nvidia/*/lib` 가 필요하다.

## 평가 (홀드아웃 test 630건, 스키마 제약 디코딩)

| 방법 | JSON 유효 | 정확 일치 | 시뮬 통과 | 지연 p50 / p95 |
| --- | --- | --- | --- | --- |
| 파인튜닝 Q4_K_M (제약 디코딩) | 100% | 95.6% | 99.4% | 565 ms / 3,997 ms |
| 규칙 기반(Client planner 포트) | 90.6% | 22.5% | 89.4% | <1 ms |
| 베이스 Qwen2.5-1.5B Q4_K_M | 미측정 | 미측정 | 미측정 | 미측정 |

- 베이스 모델·제약 없는 디코딩·손 작성 40문장 평가는 평가 프로세스가 멈춰(제약 없는 생성 단계로 추정) 중단했고 미측정이다. 재현: `python -m poppy_ai.evaluate --method gguf:<base.gguf> --label base --sets test=... manual=data/manual_test.jsonl --out <dir> --modes constrained`.
- p95 지연 약 4초는 Server 요청 타임아웃(4초) 경계라 튜닝이 필요하다.
