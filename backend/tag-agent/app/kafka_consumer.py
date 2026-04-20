from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

from .config import Settings
from .course_client import CourseServiceClient
from .models import ResourceTaggedEvent, ResourceUploadedEvent
from .tagging_service import TaggingService

logger = logging.getLogger(__name__)


class TagAgentWorker:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._course_client = CourseServiceClient(settings.course_service_base_url)
        self._tagging_service = TaggingService(settings)
        self._consumer = AIOKafkaConsumer(
            "resource.uploaded",
            bootstrap_servers=settings.kafka_bootstrap_servers,
            group_id=settings.kafka_group_id,
            auto_offset_reset="earliest",
        )
        self._producer = AIOKafkaProducer(bootstrap_servers=settings.kafka_bootstrap_servers)
        self._task: asyncio.Task[None] | None = None

    async def start(self) -> None:
        await self._producer.start()
        await self._consumer.start()
        self._task = asyncio.create_task(self._consume_loop())

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        await self._consumer.stop()
        await self._producer.stop()
        await self._course_client.close()

    async def _consume_loop(self) -> None:
        async for message in self._consumer:
            payload = message.value.decode("utf-8")
            try:
                event = ResourceUploadedEvent.model_validate_json(payload)
                await self._handle_uploaded_event(event)
            except Exception:
                logger.exception("Failed to process resource.uploaded payload=%s", payload)

    async def _handle_uploaded_event(self, event: ResourceUploadedEvent) -> None:
        context = await self._course_client.get_resource_tagging_context(event.resourceId)
        knowledge_points = await self._course_client.list_leaf_knowledge_points()
        suggestions = await self._tagging_service.suggest_tags(context, knowledge_points)

        tagged_event = ResourceTaggedEvent(
            resourceId=context.resourceId,
            chapterId=context.chapterId,
            courseId=context.courseId,
            teacherId=context.teacherId,
            title=context.title,
            storageKey=context.storageKey,
            taggingStatus="SUGGESTED" if suggestions else "UNTAGGED",
            taggingUpdatedAt=datetime.now(timezone.utc).isoformat(),
            knowledgePoints=suggestions,
        )
        await self._producer.send_and_wait(
            "resource.tagged",
            key=str(context.resourceId).encode("utf-8"),
            value=tagged_event.model_dump_json(exclude_none=True).encode("utf-8"),
        )
