from __future__ import annotations

import logging

from fastapi import FastAPI

from .config import settings
from .kafka_consumer import TagAgentWorker

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="tag-agent", version="1.0.0")
worker = TagAgentWorker(settings)


@app.on_event("startup")
async def startup() -> None:
    await worker.start()


@app.on_event("shutdown")
async def shutdown() -> None:
    await worker.stop()


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
