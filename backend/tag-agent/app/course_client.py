from __future__ import annotations

import httpx

from .models import InternalKnowledgePoint, InternalResourceTaggingContext


class CourseServiceClient:
    def __init__(self, base_url: str) -> None:
        self._client = httpx.AsyncClient(base_url=base_url, timeout=15.0)

    async def close(self) -> None:
        await self._client.aclose()

    async def get_resource_tagging_context(self, resource_id: int) -> InternalResourceTaggingContext:
        response = await self._client.get(f"/api/v1/internal/resources/{resource_id}/tagging-context")
        response.raise_for_status()
        payload = response.json()
        return InternalResourceTaggingContext.model_validate(payload["data"])

    async def list_attachable_knowledge_points(self) -> list[InternalKnowledgePoint]:
        response = await self._client.get("/api/v1/internal/knowledge-points/attachable")
        response.raise_for_status()
        payload = response.json()
        data = payload.get("data") or []
        return [InternalKnowledgePoint.model_validate(item) for item in data]

    async def list_leaf_knowledge_points(self) -> list[InternalKnowledgePoint]:
        return await self.list_attachable_knowledge_points()
