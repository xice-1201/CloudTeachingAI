from __future__ import annotations

import asyncio
import json
import logging
import urllib.error
import urllib.request
from typing import Any

from .config import Settings
from .models import ChatContext

logger = logging.getLogger(__name__)


class CourseContextClient:
    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.course_service_base_url.rstrip("/")
        self._timeout = settings.course_context_timeout_seconds

    async def build_context_prompt(self, context: ChatContext, authorization: str | None) -> str:
        if not context.has_value():
            return ""

        parts = self._fallback_parts(context)
        course = await self._get_json(f"/api/v1/courses/{context.courseId}", authorization) if context.courseId else None
        resource = await self._get_json(f"/api/v1/resources/{context.resourceId}", authorization) if context.resourceId else None

        if course:
            course_data = course.get("data", course)
            parts["课程"] = _compact_text(
                f"{course_data.get('title') or context.courseTitle or context.courseId}: "
                f"{course_data.get('description') or '暂无课程描述'}"
            )

        if resource:
            resource_data = resource.get("data", resource)
            tag_names = _extract_resource_tags(resource_data)
            parts["资源"] = _compact_text(
                f"{resource_data.get('title') or context.resourceTitle or context.resourceId}"
                f"（{resource_data.get('type') or '资源'}）："
                f"{resource_data.get('description') or '暂无资源描述'}"
            )
            if tag_names:
                parts["相关知识点"] = "、".join(tag_names[:8])

        return "\n".join(f"- {key}：{value}" for key, value in parts.items() if value)

    def _fallback_parts(self, context: ChatContext) -> dict[str, str]:
        parts: dict[str, str] = {}
        if context.courseTitle or context.courseId:
            parts["课程"] = context.courseTitle or f"课程 ID {context.courseId}"
        if context.resourceTitle or context.resourceId:
            parts["资源"] = context.resourceTitle or f"资源 ID {context.resourceId}"
        if context.knowledgePointName or context.knowledgePointId:
            parts["相关知识点"] = context.knowledgePointName or f"知识点 ID {context.knowledgePointId}"
        return parts

    async def _get_json(self, path: str, authorization: str | None) -> dict[str, Any] | None:
        return await asyncio.to_thread(self._get_json_sync, path, authorization)

    def _get_json_sync(self, path: str, authorization: str | None) -> dict[str, Any] | None:
        request = urllib.request.Request(f"{self._base_url}{path}")
        if authorization:
            request.add_header("Authorization", authorization)
        try:
            with urllib.request.urlopen(request, timeout=self._timeout) as response:
                payload = response.read().decode("utf-8")
                data = json.loads(payload)
                return data if isinstance(data, dict) else None
        except (OSError, urllib.error.URLError, json.JSONDecodeError) as error:
            logger.info("failed to fetch chat course context path=%s error=%s", path, error)
            return None


def _extract_resource_tags(resource: dict[str, Any]) -> list[str]:
    names: list[str] = []
    for key in ("knowledgePoints", "tags"):
        values = resource.get(key)
        if not isinstance(values, list):
            continue
        for item in values:
            if not isinstance(item, dict):
                continue
            value = item.get("name") or item.get("label") or item.get("knowledgePointName")
            if isinstance(value, str) and value and value not in names:
                names.append(value)
    return names


def _compact_text(value: str, limit: int = 420) -> str:
    compacted = " ".join(value.split())
    return compacted if len(compacted) <= limit else f"{compacted[:limit]}..."
