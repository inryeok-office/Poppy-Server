from fastapi.testclient import TestClient

from poppy_ai.engine import Generation
from poppy_ai.explain import explain
from poppy_ai.server import create_app


class FakeEngine:
    model_path = "/models/fake-q4_k_m.gguf"

    def __init__(self, steps=None, error=None):
        self.steps = steps
        self.error = error
        self.calls = []

    def generate(self, utterance, constrained=True):
        self.calls.append(utterance)
        return Generation("[]", self.steps, self.error, 12.0)


def test_program_draft_returns_steps_and_explanation():
    engine = FakeEngine(steps=[{"kind": "moveForward", "distanceM": 1}, {"kind": "greet"}])
    client = TestClient(create_app(engine))
    response = client.post("/v1/program-drafts", json={"utterance": "  앞으로\t1m 가고 인사해 "})
    assert response.status_code == 200
    body = response.json()
    assert body["steps"] == [{"kind": "moveForward", "distanceM": 1}, {"kind": "greet"}]
    assert body["explanation"] == "앞으로 1m 가고 인사하는 블록을 만들었어요."
    assert engine.calls == ["앞으로 1m 가고 인사해"]


def test_program_draft_rejects_empty_and_too_long_input():
    client = TestClient(create_app(FakeEngine(steps=[{"kind": "greet"}])))
    assert client.post("/v1/program-drafts", json={"utterance": " \n "}).status_code == 400
    assert client.post("/v1/program-drafts", json={"utterance": "가" * 101}).status_code == 400


def test_invalid_model_output_is_not_forwarded():
    client = TestClient(create_app(FakeEngine(steps=None, error="invalid-json")))
    assert client.post("/v1/program-drafts", json={"utterance": "춤춰"}).status_code == 502


def test_missing_model_returns_503(monkeypatch):
    monkeypatch.delenv("POPPY_AI_MODEL_PATH", raising=False)
    client = TestClient(create_app())
    assert client.post("/v1/program-drafts", json={"utterance": "춤춰"}).status_code == 503
    assert client.get("/health").json()["status"] == "UP"


def test_hint_uses_block_number():
    client = TestClient(create_app(FakeEngine()))
    body = client.post("/v1/hints", json={"programLines": ["앞으로 3m"], "violationCode": "exceeds-safe-zone", "violationBlockNumber": 2}).json()
    assert body["message"].startswith("2번째 블록에서")


def test_explain_repeat():
    steps = [{"kind": "repeat", "count": 4, "body": [{"kind": "moveForward", "distanceM": 1}, {"kind": "rotateLeft", "degrees": 90}]}]
    assert explain(steps) == "(앞으로 1m 가고 왼쪽으로 90° 돌고) 4번 반복하는 블록을 만들었어요."


def test_utterance_without_block_action_is_rejected_before_model():
    engine = FakeEngine(steps=[{"kind": "moveForward", "distanceM": 1}])
    client = TestClient(create_app(engine))
    for utterance in ["밥 먹자", "앉아", "ㅋ"]:
        assert client.post("/v1/program-drafts", json={"utterance": utterance}).status_code == 422
    assert engine.calls == []


def test_draft_over_total_distance_limit_is_scaled_down():
    square = [{"kind": "repeat", "count": 4, "body": [{"kind": "moveForward", "distanceM": 2}, {"kind": "rotateLeft", "degrees": 90}]}]
    client = TestClient(create_app(FakeEngine(steps=square)))
    body = client.post("/v1/program-drafts", json={"utterance": "네모 모양으로 걸어"}).json()
    assert body["steps"][0]["body"][0]["distanceM"] == 1.0
    assert body["steps"][0]["body"][1] == {"kind": "rotateLeft", "degrees": 90}
