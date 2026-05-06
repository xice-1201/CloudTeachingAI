from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel


class ApiResponse(BaseModel):
    code: int = 0
    message: str = "success"
    data: Any = None


class ChatMessage(BaseModel):
    id: int
    role: Literal["user", "assistant"]
    content: str
    timestamp: datetime


class ChatSession(BaseModel):
    id: int
    userId: int | str
    messages: list[ChatMessage]
    createdAt: datetime
    updatedAt: datetime

