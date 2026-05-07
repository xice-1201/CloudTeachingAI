from __future__ import annotations

import itertools
from datetime import datetime, timezone

from .models import ChatContext, ChatMessage, ChatSession


class ChatStore:
    def __init__(self) -> None:
        self._session_ids = itertools.count(1)
        self._message_ids = itertools.count(1)
        self._sessions: dict[int, ChatSession] = {}

    def list_sessions(self, user_id: int | str) -> list[ChatSession]:
        return sorted(
            (session for session in self._sessions.values() if session.userId == user_id),
            key=lambda session: session.updatedAt,
            reverse=True,
        )

    def create_session(self, user_id: int | str, context: ChatContext | None = None) -> ChatSession:
        now = datetime.now(timezone.utc)
        session_context = context or ChatContext()
        session = ChatSession(
            id=next(self._session_ids),
            userId=user_id,
            title=session_context.display_title(),
            context=session_context,
            messages=[],
            createdAt=now,
            updatedAt=now,
        )
        self._sessions[session.id] = session
        return session

    def get_session(self, user_id: int | str, session_id: int) -> ChatSession | None:
        session = self._sessions.get(session_id)
        if session is None or session.userId != user_id:
            return None
        return session

    def delete_session(self, user_id: int | str, session_id: int) -> bool:
        session = self.get_session(user_id, session_id)
        if session is None:
            return False
        del self._sessions[session_id]
        return True

    def update_context(self, session: ChatSession, context: ChatContext) -> ChatSession:
        if not context.has_value():
            return session
        session.context = context
        session.title = context.display_title()
        session.updatedAt = datetime.now(timezone.utc)
        return session

    def add_message(self, session: ChatSession, role: str, content: str) -> ChatMessage:
        message = ChatMessage(
            id=next(self._message_ids),
            role=role,  # type: ignore[arg-type]
            content=content,
            timestamp=datetime.now(timezone.utc),
        )
        session.messages.append(message)
        session.updatedAt = message.timestamp
        return message
