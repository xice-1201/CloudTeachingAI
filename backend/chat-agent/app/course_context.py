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
        self._course_base_url = settings.course_service_base_url.rstrip("/")
        self._learn_base_url = settings.learn_service_base_url.rstrip("/")
        self._timeout = settings.course_context_timeout_seconds

    async def build_context_prompt(self, context: ChatContext, authorization: str | None) -> str:
        parts = self._fallback_parts(context) if context.has_value() else {}
        course = await self._get_json(self._course_base_url, f"/api/v1/courses/{context.courseId}", authorization) if context.courseId else None
        resource = await self._get_json(self._course_base_url, f"/api/v1/resources/{context.resourceId}", authorization) if context.resourceId else None

        if course:
            course_data = course.get("data", course)
            if isinstance(course_data, dict):
                parts["课程"] = _compact_text(
                    f"{course_data.get('title') or context.courseTitle or context.courseId}: "
                    f"{course_data.get('description') or '暂无课程描述'}"
                )

        if resource:
            resource_data = resource.get("data", resource)
            if isinstance(resource_data, dict):
                tag_names = _extract_resource_tags(resource_data)
                parts["资源"] = _compact_text(
                    f"{resource_data.get('title') or context.resourceTitle or context.resourceId}"
                    f"（{resource_data.get('type') or '资源'}）："
                    f"{resource_data.get('description') or '暂无资源描述'}"
                )
                if tag_names:
                    parts["相关知识点"] = "、".join(tag_names[:8])

        learning_parts = await self._build_learning_profile_parts(context, authorization)
        parts.update(learning_parts)
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

    async def _build_learning_profile_parts(self, context: ChatContext, authorization: str | None) -> dict[str, str]:
        if not authorization:
            return {}

        ability_map_task = self._get_json(self._learn_base_url, "/api/v1/learn/ability-map", authorization)
        learning_path_task = self._get_json(self._learn_base_url, "/api/v1/learn/path", authorization)
        course_progress_task = (
            self._get_json(self._learn_base_url, f"/api/v1/learn/courses/{context.courseId}/progress", authorization)
            if context.courseId
            else _none_async()
        )
        ability_map, learning_path, course_progress = await asyncio.gather(
            ability_map_task,
            learning_path_task,
            course_progress_task,
        )

        parts: dict[str, str] = {}
        ability_summary = _summarize_ability_map(_unwrap_data(ability_map))
        if ability_summary:
            parts["学生能力画像"] = ability_summary

        path_summary = _summarize_learning_path(_unwrap_data(learning_path))
        if path_summary:
            parts["当前学习路线"] = path_summary

        progress_summary = _summarize_course_progress(_unwrap_data(course_progress))
        if progress_summary:
            parts["当前课程进度"] = progress_summary

        return parts

    async def _get_json(self, base_url: str, path: str, authorization: str | None) -> dict[str, Any] | None:
        return await asyncio.to_thread(self._get_json_sync, base_url, path, authorization)

    def _get_json_sync(self, base_url: str, path: str, authorization: str | None) -> dict[str, Any] | None:
        request = urllib.request.Request(f"{base_url}{path}")
        if authorization:
            request.add_header("Authorization", authorization)
        try:
            with urllib.request.urlopen(request, timeout=self._timeout) as response:
                payload = response.read().decode("utf-8")
                data = json.loads(payload)
                return data if isinstance(data, dict) else None
        except (OSError, urllib.error.URLError, json.JSONDecodeError) as error:
            logger.info("failed to fetch chat context path=%s error=%s", path, error)
            return None


async def _none_async() -> None:
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


def _unwrap_data(payload: dict[str, Any] | None) -> Any:
    if not isinstance(payload, dict):
        return None
    return payload.get("data", payload)


def _summarize_ability_map(value: Any) -> str:
    if not isinstance(value, list) or not value:
        return ""

    items = [item for item in value if isinstance(item, dict)]
    if not items:
        return ""

    sorted_items = sorted(items, key=lambda item: float(item.get("masteryLevel") or 0))
    weak = [_format_ability_item(item) for item in sorted_items[:3]]
    strong = [_format_ability_item(item) for item in reversed(sorted_items[-2:])]
    weak_text = "；".join(item for item in weak if item)
    strong_text = "；".join(item for item in strong if item)
    summary_parts = []
    if weak_text:
        summary_parts.append(f"薄弱知识点：{weak_text}")
    if strong_text:
        summary_parts.append(f"掌握较好：{strong_text}")
    return "；".join(summary_parts)


def _format_ability_item(item: dict[str, Any]) -> str:
    name = item.get("knowledgePointPath") or item.get("knowledgePointName")
    if not name:
        return ""
    mastery = _percent(item.get("masteryLevel"))
    confidence = _percent(item.get("confidence"))
    progress = _percent(item.get("progressScore"))
    source = item.get("source") or "UNKNOWN"
    return f"{name}（掌握度{mastery}，信心{confidence}，学习进度{progress}，来源{source}）"


def _summarize_learning_path(value: Any) -> str:
    if not isinstance(value, dict):
        return ""

    focus_points = value.get("focusKnowledgePoints")
    resources = value.get("resources")
    focus_items = [item for item in focus_points if isinstance(item, dict)] if isinstance(focus_points, list) else []
    resource_items = [item for item in resources if isinstance(item, dict)] if isinstance(resources, list) else []

    parts: list[str] = []
    if focus_items:
        focus_text = "、".join(
            _compact_text(
                f"{item.get('knowledgePointPath') or item.get('knowledgePointName')}"
                f"（掌握度{_percent(item.get('masteryLevel'))}）",
                80,
            )
            for item in focus_items[:4]
            if item.get("knowledgePointPath") or item.get("knowledgePointName")
        )
        if focus_text:
            parts.append(f"推荐重点：{focus_text}")

    if resource_items:
        resource_text = "、".join(
            _compact_text(
                f"{item.get('courseTitle') or '课程'} / {item.get('resourceTitle') or '资源'}"
                f"（{item.get('statusLabel') or item.get('learningStatus') or '待学习'}，"
                f"进度{_percent(item.get('currentProgress'))}）",
                100,
            )
            for item in resource_items[:4]
            if item.get("resourceTitle")
        )
        if resource_text:
            parts.append(f"下一步资源：{resource_text}")

    return "；".join(parts)


def _summarize_course_progress(value: Any) -> str:
    if not isinstance(value, dict) or value.get("courseId") is None:
        return ""
    return (
        f"课程 {value.get('courseId')} 已完成{_percent(value.get('progress'))}，"
        f"资源完成 {value.get('completedResources') or 0}/{value.get('totalResources') or 0}，"
        f"最近学习：{value.get('lastLearnedAt') or '暂无记录'}"
    )


def _percent(value: Any) -> str:
    try:
        numeric = float(value or 0)
    except (TypeError, ValueError):
        numeric = 0.0
    return f"{round(max(0.0, min(1.0, numeric)) * 100)}%"


def _compact_text(value: str, limit: int = 420) -> str:
    compacted = " ".join(value.split())
    return compacted if len(compacted) <= limit else f"{compacted[:limit]}..."
