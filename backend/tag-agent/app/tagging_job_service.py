from __future__ import annotations

from datetime import datetime, timezone

from .config import Settings
from .course_client import CourseServiceClient
from .models import ResourceTaggedEvent, ResourceUploadedEvent
from .tagging_service import TaggingService


class TaggingJobService:
    def __init__(self, settings: Settings) -> None:
        self._course_client = CourseServiceClient(settings.course_service_base_url)
        self._tagging_service = TaggingService(settings)

    async def close(self) -> None:
        await self._course_client.close()

    async def build_tagged_event(self, event: ResourceUploadedEvent) -> ResourceTaggedEvent:
        context = await self._course_client.get_resource_tagging_context(event.resourceId)
        knowledge_points = await self._course_client.list_attachable_knowledge_points()
        suggestions = await self._tagging_service.suggest_tags(context, knowledge_points)

        return ResourceTaggedEvent(
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
