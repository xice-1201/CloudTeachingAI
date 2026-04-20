from __future__ import annotations

import asyncio
import json
import logging
import re
from typing import Any

from .config import Settings
from .models import (
    InternalKnowledgePoint,
    InternalResourceTaggingContext,
    ResourceTaggedKnowledgePointEvent,
)

logger = logging.getLogger(__name__)

try:
    from openai import OpenAI
except Exception:  # pragma: no cover - optional dependency at runtime
    OpenAI = None


class TaggingService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._llm_client = None
        if settings.deepseek_api_key and OpenAI is not None:
            self._llm_client = OpenAI(
                api_key=settings.deepseek_api_key,
                base_url=settings.deepseek_base_url,
            )

    async def suggest_tags(
        self,
        context: InternalResourceTaggingContext,
        knowledge_points: list[InternalKnowledgePoint],
    ) -> list[ResourceTaggedKnowledgePointEvent]:
        llm_suggestions = await self._suggest_with_llm(context, knowledge_points)
        if llm_suggestions:
            return llm_suggestions[: self._settings.tag_limit]
        return self._suggest_with_rules(context, knowledge_points)[: self._settings.tag_limit]

    async def _suggest_with_llm(
        self,
        context: InternalResourceTaggingContext,
        knowledge_points: list[InternalKnowledgePoint],
    ) -> list[ResourceTaggedKnowledgePointEvent]:
        if self._llm_client is None:
            return []

        prompt_points = [
            {
                "id": item.id,
                "name": item.name,
                "keywords": item.keywords,
                "path": item.path,
            }
            for item in knowledge_points
        ]
        messages = [
            {
                "role": "system",
                "content": (
                    "你是课程资源知识点标注助手。请根据资源标题、简介和可用知识点候选，"
                    "输出最相关的知识点列表。只返回 JSON，对不确定的候选不要强行选择。"
                ),
            },
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "resource": {
                            "title": context.title,
                            "description": context.description,
                            "type": context.type,
                            "storageKey": context.storageKey,
                        },
                        "knowledgePoints": prompt_points,
                        "output": {
                            "matches": [
                                {
                                    "knowledgePointId": 1,
                                    "confidence": 0.91,
                                    "reason": "简短理由",
                                }
                            ]
                        },
                    },
                    ensure_ascii=False,
                ),
            },
        ]

        try:
            response = await asyncio.to_thread(
                self._llm_client.chat.completions.create,
                model=self._settings.deepseek_model,
                messages=messages,
                temperature=0.2,
                response_format={"type": "json_object"},
            )
            content = response.choices[0].message.content or "{}"
            payload = json.loads(content)
            matches = payload.get("matches") or []
            results: list[ResourceTaggedKnowledgePointEvent] = []
            valid_ids = {item.id for item in knowledge_points}
            for match in matches:
                knowledge_point_id = match.get("knowledgePointId")
                if knowledge_point_id not in valid_ids:
                    continue
                confidence = float(match.get("confidence", 0.0))
                if confidence <= 0:
                    continue
                results.append(
                    ResourceTaggedKnowledgePointEvent(
                        knowledgePointId=knowledge_point_id,
                        confidence=max(0.0, min(1.0, confidence)),
                        reason=match.get("reason"),
                    )
                )
            return results
        except Exception:
            logger.exception("LLM tagging failed, fallback to rule-based tagging")
            return []

    def _suggest_with_rules(
        self,
        context: InternalResourceTaggingContext,
        knowledge_points: list[InternalKnowledgePoint],
    ) -> list[ResourceTaggedKnowledgePointEvent]:
        normalized_title = self._normalize_text(context.title)
        normalized_body = self._normalize_text(
            " ".join(
                part
                for part in [context.title, context.description or "", context.storageKey]
                if part
            )
        )
        results: list[tuple[float, ResourceTaggedKnowledgePointEvent]] = []

        for point in knowledge_points:
            score = 0.0
            reasons: list[str] = []

            point_name = self._normalize_text(point.name)
            if point_name and point_name in normalized_title:
                score += 0.72
                reasons.append("标题命中知识点名称")
            elif point_name and point_name in normalized_body:
                score += 0.6
                reasons.append("资源内容命中知识点名称")

            for keyword in self._split_keywords(point.keywords):
                normalized_keyword = self._normalize_text(keyword)
                if normalized_keyword and normalized_keyword in normalized_body:
                    score += 0.18
                    reasons.append(f"命中关键词 {keyword}")

            for path_part in self._split_path(point.path):
                normalized_path_part = self._normalize_text(path_part)
                if normalized_path_part and normalized_path_part in normalized_body:
                    score += 0.06
                    reasons.append(f"命中路径节点 {path_part}")

            if score < 0.18:
                continue

            results.append(
                (
                    score,
                    ResourceTaggedKnowledgePointEvent(
                        knowledgePointId=point.id,
                        confidence=round(min(0.99, score), 2),
                        reason="；".join(dict.fromkeys(reasons)) if reasons else "规则匹配",
                    ),
                )
            )

        results.sort(key=lambda item: (-item[0], item[1].knowledgePointId))
        return [item[1] for item in results]

    def _normalize_text(self, value: str | None) -> str:
        if not value:
            return ""
        return re.sub(r"[\W_]+", " ", value.strip().lower())

    def _split_keywords(self, keywords: str | None) -> list[str]:
        if not keywords:
            return []
        return [item.strip() for item in re.split(r"[,\n;\r]+", keywords) if item.strip()]

    def _split_path(self, path: str | None) -> list[str]:
        if not path:
            return []
        return [item.strip() for item in path.split("/") if item.strip()]
