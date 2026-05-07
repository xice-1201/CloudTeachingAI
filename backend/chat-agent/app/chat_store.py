from __future__ import annotations

import itertools
import json
from datetime import datetime, timezone
from typing import Protocol

from .config import Settings
from .models import ChatContext, ChatMessage, ChatSession


class ChatStore(Protocol):
    def list_sessions(self, user_id: int | str) -> list[ChatSession]: ...

    def create_session(self, user_id: int | str, context: ChatContext | None = None) -> ChatSession: ...

    def get_session(self, user_id: int | str, session_id: int) -> ChatSession | None: ...

    def delete_session(self, user_id: int | str, session_id: int) -> bool: ...

    def update_context(self, session: ChatSession, context: ChatContext) -> ChatSession: ...

    def add_message(self, session: ChatSession, role: str, content: str) -> ChatMessage: ...


class MemoryChatStore:
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


class PostgresChatStore:
    def __init__(self, database_url: str) -> None:
        self._database_url = database_url
        self._ensure_schema()

    def list_sessions(self, user_id: int | str) -> list[ChatSession]:
        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT id, user_id, title, context, created_at, updated_at
                FROM chat_sessions
                WHERE user_id = %s
                ORDER BY updated_at DESC
                """,
                (_user_key(user_id),),
            ).fetchall()
        return [self._row_to_session(row, []) for row in rows]

    def create_session(self, user_id: int | str, context: ChatContext | None = None) -> ChatSession:
        now = datetime.now(timezone.utc)
        session_context = context or ChatContext()
        with self._connect() as conn:
            row = conn.execute(
                """
                INSERT INTO chat_sessions (user_id, title, context, created_at, updated_at)
                VALUES (%s, %s, %s, %s, %s)
                RETURNING id, user_id, title, context, created_at, updated_at
                """,
                (
                    _user_key(user_id),
                    session_context.display_title(),
                    _jsonb(session_context.model_dump(mode="json")),
                    now,
                    now,
                ),
            ).fetchone()
        return self._row_to_session(row, [])

    def get_session(self, user_id: int | str, session_id: int) -> ChatSession | None:
        with self._connect() as conn:
            session_row = conn.execute(
                """
                SELECT id, user_id, title, context, created_at, updated_at
                FROM chat_sessions
                WHERE id = %s AND user_id = %s
                """,
                (session_id, _user_key(user_id)),
            ).fetchone()
            if session_row is None:
                return None
            message_rows = conn.execute(
                """
                SELECT id, role, content, timestamp
                FROM chat_messages
                WHERE session_id = %s
                ORDER BY id ASC
                """,
                (session_id,),
            ).fetchall()
        return self._row_to_session(session_row, [self._row_to_message(row) for row in message_rows])

    def delete_session(self, user_id: int | str, session_id: int) -> bool:
        with self._connect() as conn:
            result = conn.execute(
                "DELETE FROM chat_sessions WHERE id = %s AND user_id = %s",
                (session_id, _user_key(user_id)),
            )
            return result.rowcount > 0

    def update_context(self, session: ChatSession, context: ChatContext) -> ChatSession:
        if not context.has_value():
            return session
        now = datetime.now(timezone.utc)
        with self._connect() as conn:
            row = conn.execute(
                """
                UPDATE chat_sessions
                SET title = %s, context = %s, updated_at = %s
                WHERE id = %s AND user_id = %s
                RETURNING id, user_id, title, context, created_at, updated_at
                """,
                (
                    context.display_title(),
                    _jsonb(context.model_dump(mode="json")),
                    now,
                    session.id,
                    _user_key(session.userId),
                ),
            ).fetchone()
        updated = self._row_to_session(row, session.messages)
        session.title = updated.title
        session.context = updated.context
        session.updatedAt = updated.updatedAt
        return session

    def add_message(self, session: ChatSession, role: str, content: str) -> ChatMessage:
        now = datetime.now(timezone.utc)
        with self._connect() as conn:
            row = conn.execute(
                """
                INSERT INTO chat_messages (session_id, role, content, timestamp)
                VALUES (%s, %s, %s, %s)
                RETURNING id, role, content, timestamp
                """,
                (session.id, role, content, now),
            ).fetchone()
            conn.execute(
                "UPDATE chat_sessions SET updated_at = %s WHERE id = %s",
                (now, session.id),
            )
        message = self._row_to_message(row)
        session.messages.append(message)
        session.updatedAt = message.timestamp
        return message

    def _ensure_schema(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id BIGSERIAL PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    context JSONB NOT NULL DEFAULT '{}'::jsonb,
                    created_at TIMESTAMPTZ NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL
                )
                """
            )
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id BIGSERIAL PRIMARY KEY,
                    session_id BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
                    role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
                    content TEXT NOT NULL,
                    timestamp TIMESTAMPTZ NOT NULL
                )
                """
            )
            conn.execute("CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_updated ON chat_sessions (user_id, updated_at DESC)")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id ON chat_messages (session_id, id)")

    def _connect(self):
        import psycopg
        from psycopg.rows import dict_row

        return psycopg.connect(self._database_url, row_factory=dict_row)

    def _row_to_session(self, row: dict, messages: list[ChatMessage]) -> ChatSession:
        context = row.get("context") or {}
        if isinstance(context, str):
            context = json.loads(context)
        return ChatSession(
            id=row["id"],
            userId=_restore_user_id(row["user_id"]),
            title=row["title"],
            context=ChatContext.model_validate(context),
            messages=messages,
            createdAt=row["created_at"],
            updatedAt=row["updated_at"],
        )

    def _row_to_message(self, row: dict) -> ChatMessage:
        return ChatMessage(
            id=row["id"],
            role=row["role"],
            content=row["content"],
            timestamp=row["timestamp"],
        )


def create_chat_store(settings: Settings) -> ChatStore:
    if settings.chat_database_url:
        return PostgresChatStore(settings.chat_database_url)
    return MemoryChatStore()


def _user_key(user_id: int | str) -> str:
    return str(user_id)


def _restore_user_id(user_id: str) -> int | str:
    return int(user_id) if user_id.isdigit() else user_id


def _jsonb(value: dict):
    from psycopg.types.json import Jsonb

    return Jsonb(value)
