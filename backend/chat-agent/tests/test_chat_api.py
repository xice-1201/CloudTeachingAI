from __future__ import annotations

import importlib
import base64
import asyncio
import json
import sys
from pathlib import Path

from fastapi.testclient import TestClient


class FakeResponder:
    async def stream_reply(self, history, message, context_prompt=""):
        yield "收到："
        if context_prompt:
            yield context_prompt
        yield message


class FakeContextClient:
    async def build_context_prompt(self, context, authorization):
        if context.courseTitle:
            return f"课程：{context.courseTitle}"
        return ""


def bearer_for_user(user_id: str) -> str:
    header = base64.urlsafe_b64encode(json.dumps({"alg": "none"}).encode("utf-8")).decode("utf-8").rstrip("=")
    payload = base64.urlsafe_b64encode(json.dumps({"sub": user_id}).encode("utf-8")).decode("utf-8").rstrip("=")
    return f"Bearer {header}.{payload}.signature"


def load_chat_main():
    for module_name in list(sys.modules):
        if module_name == "app" or module_name.startswith("app."):
            del sys.modules[module_name]
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    return importlib.import_module("app.main")


def test_chat_session_lifecycle_and_streaming_reply():
    chat_main = load_chat_main()
    chat_main.store = chat_main.MemoryChatStore()
    chat_main.responder = FakeResponder()

    client = TestClient(chat_main.app)
    headers = {"Authorization": bearer_for_user("42")}

    created = client.post("/api/v1/chat/sessions", headers=headers).json()
    assert created["code"] == 0
    session_id = created["data"]["id"]
    assert created["data"]["userId"] == 42
    assert created["data"]["title"] == "通用问答"

    listed = client.get("/api/v1/chat/sessions", headers=headers).json()
    assert [session["id"] for session in listed["data"]] == [session_id]

    with client.stream(
        "GET",
        f"/api/v1/chat/sessions/{session_id}/messages",
        params={"message": "请总结这节课"},
        headers=headers,
    ) as response:
        body = response.read().decode("utf-8")

    assert response.status_code == 200
    assert "data: 收到：" in body
    assert "data: 请总结这节课" in body
    assert "data: [DONE]" in body

    session = client.get(f"/api/v1/chat/sessions/{session_id}", headers=headers).json()
    assert [message["role"] for message in session["data"]["messages"]] == ["user", "assistant"]
    assert session["data"]["messages"][1]["content"] == "收到：请总结这节课"

    deleted = client.delete(f"/api/v1/chat/sessions/{session_id}", headers=headers).json()
    assert deleted["code"] == 0
    missing = client.get(f"/api/v1/chat/sessions/{session_id}", headers=headers)
    assert missing.status_code == 404


def test_chat_sessions_are_scoped_by_user():
    chat_main = load_chat_main()
    chat_main.store = chat_main.MemoryChatStore()
    chat_main.responder = FakeResponder()

    client = TestClient(chat_main.app)
    session_id = client.post(
        "/api/v1/chat/sessions",
        headers={"Authorization": bearer_for_user("teacher-a")},
    ).json()["data"]["id"]

    response = client.get(
        f"/api/v1/chat/sessions/{session_id}",
        headers={"Authorization": bearer_for_user("teacher-b")},
    )

    assert response.status_code == 404


def test_chat_stream_accepts_event_source_query_auth():
    chat_main = load_chat_main()
    chat_main.store = chat_main.MemoryChatStore()
    chat_main.responder = FakeResponder()

    client = TestClient(chat_main.app)
    token = bearer_for_user("77")

    session_id = client.post(
        "/api/v1/chat/sessions",
        headers={"Authorization": token},
    ).json()["data"]["id"]

    with client.stream(
        "GET",
        f"/api/v1/chat/sessions/{session_id}/messages",
        params={"message": "继续讲解", "Authorization": token},
    ) as response:
        body = response.read().decode("utf-8")

    assert response.status_code == 200
    assert "data: 收到：" in body
    assert "data: 继续讲解" in body


def test_chat_stream_injects_course_context():
    chat_main = load_chat_main()
    chat_main.store = chat_main.MemoryChatStore()
    chat_main.responder = FakeResponder()
    chat_main.context_client = FakeContextClient()

    client = TestClient(chat_main.app)
    token = bearer_for_user("88")
    session_id = client.post(
        "/api/v1/chat/sessions",
        headers={"Authorization": token},
    ).json()["data"]["id"]

    with client.stream(
        "GET",
        f"/api/v1/chat/sessions/{session_id}/messages",
        params={"message": "讲一下重点", "Authorization": token, "courseTitle": "线性代数"},
    ) as response:
        body = response.read().decode("utf-8")

    assert response.status_code == 200
    assert "data: 课程：线性代数" in body

    session = client.get(
        f"/api/v1/chat/sessions/{session_id}",
        headers={"Authorization": token},
    ).json()["data"]
    assert session["title"] == "线性代数"
    assert session["context"]["courseTitle"] == "线性代数"


def test_chat_session_persists_context_on_create():
    chat_main = load_chat_main()
    chat_main.store = chat_main.MemoryChatStore()

    client = TestClient(chat_main.app)
    created = client.post(
        "/api/v1/chat/sessions",
        params={
            "userId": "100",
            "courseId": "12",
            "courseTitle": "高等数学",
            "resourceId": "34",
            "resourceTitle": "导数定义",
            "returnUrl": "/courses/12/learn/34",
            "returnLabel": "返回资源",
        },
    ).json()["data"]

    assert created["title"] == "导数定义"
    assert created["context"]["courseId"] == 12
    assert created["context"]["resourceTitle"] == "导数定义"
    assert created["context"]["returnUrl"] == "/courses/12/learn/34"


def test_chat_session_can_use_user_id_query_without_token():
    chat_main = load_chat_main()
    chat_main.store = chat_main.MemoryChatStore()
    chat_main.responder = FakeResponder()

    client = TestClient(chat_main.app)

    session_id = client.post(
        "/api/v1/chat/sessions",
        params={"userId": "99"},
    ).json()["data"]["id"]

    listed = client.get("/api/v1/chat/sessions", params={"userId": "99"}).json()

    assert [session["id"] for session in listed["data"]] == [session_id]


def test_internal_cleanup_deletes_all_user_chat_sessions():
    chat_main = load_chat_main()
    chat_main.store = chat_main.MemoryChatStore()
    chat_main.responder = FakeResponder()

    client = TestClient(chat_main.app)
    headers = {"Authorization": bearer_for_user("56")}

    first_id = client.post("/api/v1/chat/sessions", headers=headers).json()["data"]["id"]
    second_id = client.post("/api/v1/chat/sessions", headers=headers).json()["data"]["id"]
    other_id = client.post("/api/v1/chat/sessions", headers={"Authorization": bearer_for_user("57")}).json()["data"]["id"]

    cleanup = client.delete("/api/v1/internal/users/56/chat-data").json()

    assert cleanup["code"] == 0
    assert cleanup["data"]["deletedSessions"] == 2
    assert client.get(f"/api/v1/chat/sessions/{first_id}", headers=headers).status_code == 404
    assert client.get(f"/api/v1/chat/sessions/{second_id}", headers=headers).status_code == 404
    assert client.get(
        f"/api/v1/chat/sessions/{other_id}",
        headers={"Authorization": bearer_for_user("57")},
    ).status_code == 200


def test_context_prompt_includes_student_learning_profile(monkeypatch):
    load_chat_main()
    from app.config import Settings
    from app.course_context import CourseContextClient
    from app.models import ChatContext

    class FakeResponse:
        def __init__(self, payload):
            self._payload = payload

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, traceback):
            return False

        def read(self):
            return json.dumps(self._payload).encode("utf-8")

    def fake_urlopen(request, timeout):
        url = request.full_url
        if url.endswith("/api/v1/learn/ability-map"):
            return FakeResponse({
                "code": 0,
                "data": [
                    {
                        "knowledgePointName": "循环结构",
                        "knowledgePointPath": "Python/基础/循环结构",
                        "masteryLevel": 0.32,
                        "confidence": 0.7,
                        "progressScore": 0.4,
                        "source": "TEST_AND_PROGRESS",
                    },
                    {
                        "knowledgePointName": "变量",
                        "knowledgePointPath": "Python/基础/变量",
                        "masteryLevel": 0.86,
                        "confidence": 0.8,
                        "progressScore": 0.9,
                        "source": "LEARNING_PROGRESS",
                    },
                ],
            })
        if url.endswith("/api/v1/learn/path"):
            return FakeResponse({
                "code": 0,
                "data": {
                    "focusKnowledgePoints": [
                        {
                            "knowledgePointName": "循环结构",
                            "knowledgePointPath": "Python/基础/循环结构",
                            "masteryLevel": 0.32,
                        }
                    ],
                    "resources": [
                        {
                            "courseTitle": "Python 入门",
                            "resourceTitle": "while 循环练习",
                            "statusLabel": "继续学习",
                            "currentProgress": 0.25,
                        }
                    ],
                },
            })
        if url.endswith("/api/v1/learn/courses/12/progress"):
            return FakeResponse({
                "code": 0,
                "data": {
                    "courseId": 12,
                    "progress": 0.45,
                    "completedResources": 2,
                    "totalResources": 5,
                    "lastLearnedAt": "2026-05-10T09:00:00Z",
                },
            })
        return FakeResponse({"code": 0, "data": None})

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    client = CourseContextClient(Settings())

    prompt = asyncio.run(client.build_context_prompt(ChatContext(courseId=12), "Bearer token"))

    assert "学生能力画像" in prompt
    assert "Python/基础/循环结构" in prompt
    assert "当前学习路线" in prompt
    assert "while 循环练习" in prompt
    assert "当前课程进度" in prompt
