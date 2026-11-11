from __future__ import annotations

import os
import threading
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from .blocks import AI_INPUT_MAX_LENGTH, normalize_utterance
from .explain import explain
from .guard import cap_total_distance, is_actionable
from .hints import hint_for

MODEL_PATH_ENV = "POPPY_AI_MODEL_PATH"


class DraftRequest(BaseModel):
    utterance: str = Field(max_length=1000)


class HintRequest(BaseModel):
    programLines: list[str] = Field(default_factory=list, max_length=40)
    violationCode: Optional[str] = None
    violationMessage: Optional[str] = Field(default=None, max_length=300)
    violationBlockNumber: Optional[int] = None


def create_app(engine=None) -> FastAPI:
    app = FastAPI(title="poppy-ai-inference")
    state = {"engine": engine}
    lock = threading.Lock()

    def get_engine():
        if state["engine"] is None:
            path = os.environ.get(MODEL_PATH_ENV)
            if not path:
                raise HTTPException(status_code=503, detail="model not configured")
            from .engine import DraftEngine

            state["engine"] = DraftEngine(path)
        return state["engine"]

    @app.get("/health")
    def health():
        model = os.path.basename(os.environ.get(MODEL_PATH_ENV, "")) or None
        return {"status": "UP", "model": model, "loaded": state["engine"] is not None}

    @app.post("/v1/program-drafts")
    def program_drafts(request: DraftRequest):
        utterance = normalize_utterance(request.utterance)
        if not utterance or len(utterance) > AI_INPUT_MAX_LENGTH:
            raise HTTPException(status_code=400, detail="utterance must be 1..100 characters")
        if not is_actionable(utterance):
            raise HTTPException(status_code=422, detail="utterance has no block action")
        engine = get_engine()
        with lock:
            generation = engine.generate(utterance)
        if generation.steps is None:
            raise HTTPException(status_code=502, detail=generation.error or "invalid output")
        steps = cap_total_distance(generation.steps)
        return {
            "steps": steps,
            "explanation": explain(steps),
            "model": os.path.basename(getattr(engine, "model_path", "")),
            "latencyMs": round(generation.latency_ms),
        }

    @app.post("/v1/hints")
    def hints(request: HintRequest):
        return {"message": hint_for(request.violationCode, request.violationBlockNumber, request.violationMessage)}

    return app


app = create_app()
