from __future__ import annotations

import logging

from fastapi import FastAPI

from .config import settings
from .models import ApiResponse, ResourceUploadedEvent
from .tagging_job_service import TaggingJobService

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="tag-agent", version="1.0.0")
tagging_job_service = TaggingJobService(settings)
worker = None


@app.on_event("startup")
async def startup() -> None:
    global worker
    if settings.kafka_enabled:
        from .kafka_consumer import TagAgentWorker

        worker = TagAgentWorker(settings, tagging_job_service)
        await worker.start()


@app.on_event("shutdown")
async def shutdown() -> None:
    if worker is not None:
        await worker.stop()
    await tagging_job_service.close()


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "kafka": "enabled" if settings.kafka_enabled else "disabled"}


@app.post("/api/v1/internal/resource-tagging/jobs")
async def create_resource_tagging_job(event: ResourceUploadedEvent) -> ApiResponse:
    tagged_event = await tagging_job_service.build_tagged_event(event)
    return ApiResponse(data=tagged_event.model_dump(exclude_none=True))
