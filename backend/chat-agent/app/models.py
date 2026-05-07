from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


class ApiResponse(BaseModel):
    code: int = 0
    message: str = "success"
    data: Any = None


class ChatMessage(BaseModel):
    id: int
    role: Literal["user", "assistant"]
    content: str
    timestamp: datetime


class ChatContext(BaseModel):
    courseId: int | None = None
    courseTitle: str | None = None
    resourceId: int | None = None
    resourceTitle: str | None = None
    knowledgePointId: int | None = None
    knowledgePointName: str | None = None
    returnUrl: str | None = None
    returnLabel: str | None = None

    def has_value(self) -> bool:
        return any(
            value is not None and value != ""
            for value in (
                self.courseId,
                self.courseTitle,
                self.resourceId,
                self.resourceTitle,
                self.knowledgePointId,
                self.knowledgePointName,
            )
        )

    def display_title(self) -> str:
        return self.resourceTitle or self.knowledgePointName or self.courseTitle or "通用问答"


class ChatSession(BaseModel):
    id: int
    userId: int | str
    title: str
    context: ChatContext = Field(default_factory=ChatContext)
    messages: list[ChatMessage]
    createdAt: datetime
    updatedAt: datetime
