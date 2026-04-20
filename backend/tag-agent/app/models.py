from __future__ import annotations

from pydantic import BaseModel, Field


class ApiResponse(BaseModel):
    code: int = 0
    message: str = "success"
    data: object | None = None


class InternalKnowledgePoint(BaseModel):
    id: int
    parentId: int | None = None
    name: str
    description: str | None = None
    keywords: str | None = None
    nodeType: str
    path: str | None = None


class InternalResourceTaggingContext(BaseModel):
    resourceId: int
    chapterId: int
    courseId: int
    teacherId: int
    title: str
    description: str | None = None
    type: str
    storageKey: str


class ResourceUploadedEvent(BaseModel):
    resourceId: int
    chapterId: int
    courseId: int
    teacherId: int
    title: str
    description: str | None = None
    type: str
    storageKey: str


class ResourceTaggedKnowledgePointEvent(BaseModel):
    knowledgePointId: int
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str | None = None


class ResourceTaggedEvent(BaseModel):
    resourceId: int
    chapterId: int
    courseId: int
    teacherId: int
    title: str
    storageKey: str
    taggingStatus: str
    taggingUpdatedAt: str | None = None
    knowledgePoints: list[ResourceTaggedKnowledgePointEvent]
