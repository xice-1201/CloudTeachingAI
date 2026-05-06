from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from .auth import resolve_user_id
from .chat_store import ChatStore
from .config import settings
from .llm import ChatResponder
from .models import ApiResponse, ChatSession

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="chat-agent", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

store = ChatStore()
responder = ChatResponder(settings)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/api/v1/chat/sessions")
async def list_sessions(user_id: Annotated[int | str, Depends(resolve_user_id)]) -> ApiResponse:
    return ok([serialize_session(session) for session in store.list_sessions(user_id)])


@app.post("/api/v1/chat/sessions")
async def create_session(user_id: Annotated[int | str, Depends(resolve_user_id)]) -> ApiResponse:
    return ok(serialize_session(store.create_session(user_id)))


@app.get("/api/v1/chat/sessions/{session_id}")
async def get_session(
    session_id: int,
    user_id: Annotated[int | str, Depends(resolve_user_id)],
) -> ApiResponse:
    session = require_session(user_id, session_id)
    return ok(serialize_session(session))


@app.delete("/api/v1/chat/sessions/{session_id}")
async def delete_session(
    session_id: int,
    user_id: Annotated[int | str, Depends(resolve_user_id)],
) -> ApiResponse:
    store.delete_session(user_id, session_id)
    return ok(None)


@app.get("/api/v1/chat/sessions/{session_id}/messages")
async def send_message(
    session_id: int,
    user_id: Annotated[int | str, Depends(resolve_user_id)],
    message: Annotated[str, Query(min_length=1)],
) -> StreamingResponse:
    session = require_session(user_id, session_id)
    return StreamingResponse(
        stream_message(session, message),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


async def stream_message(session: ChatSession, message: str) -> AsyncIterator[str]:
    user_message = store.add_message(session, "user", message)
    chunks: list[str] = []
    try:
        async for chunk in responder.stream_reply(session.messages[:-1], message):
            chunks.append(chunk)
            yield sse_data(chunk)
        store.add_message(session, "assistant", "".join(chunks))
        yield sse_data("[DONE]")
    except Exception as error:
        logger.exception("chat streaming failed session_id=%s message_id=%s", session.id, user_message.id)
        yield sse_data(f"AI 服务暂时不可用：{error}")
        yield sse_data("[DONE]")


def require_session(user_id: int | str, session_id: int) -> ChatSession:
    session = store.get_session(user_id, session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Chat session not found")
    return session


def serialize_session(session: ChatSession) -> dict[str, object]:
    return session.model_dump(mode="json")


def ok(data: object) -> ApiResponse:
    return ApiResponse(data=data)


def sse_data(data: str) -> str:
    lines = data.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    return "".join(f"data: {line}\n" for line in lines) + "\n"
