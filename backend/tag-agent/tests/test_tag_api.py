from __future__ import annotations

import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient


def load_tag_main():
    for module_name in list(sys.modules):
        if module_name == "app" or module_name.startswith("app."):
            del sys.modules[module_name]
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    return importlib.import_module("app.main")


def test_health_reports_kafka_disabled_by_default():
    tag_main = load_tag_main()
    client = TestClient(tag_main.app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "kafka": "disabled"}


def test_create_resource_tagging_job_returns_tagged_event():
    tag_main = load_tag_main()
    tag_models = importlib.import_module("app.models")

    async def fake_build_tagged_event(event):
        return tag_models.ResourceTaggedEvent(
            resourceId=event.resourceId,
            chapterId=event.chapterId,
            courseId=event.courseId,
            teacherId=event.teacherId,
            title=event.title,
            storageKey=event.storageKey,
            taggingStatus="SUGGESTED",
            taggingUpdatedAt="2026-05-06T00:00:00+00:00",
            knowledgePoints=[
                tag_models.ResourceTaggedKnowledgePointEvent(
                    knowledgePointId=7,
                    confidence=0.91,
                    reason="命中章节关键概念",
                )
            ],
        )

    tag_main.tagging_job_service.build_tagged_event = fake_build_tagged_event
    client = TestClient(tag_main.app)

    response = client.post(
        "/api/v1/internal/resource-tagging/jobs",
        json={
            "resourceId": 1001,
            "chapterId": 201,
            "courseId": 301,
            "teacherId": 401,
            "title": "函数极限讲义",
            "description": "包含函数极限的定义和例题",
            "type": "PDF",
            "storageKey": "resources/1001.pdf",
        },
    )

    payload = response.json()
    assert response.status_code == 200
    assert payload["code"] == 0
    assert payload["data"]["resourceId"] == 1001
    assert payload["data"]["taggingStatus"] == "SUGGESTED"
    assert payload["data"]["knowledgePoints"] == [
        {"knowledgePointId": 7, "confidence": 0.91, "reason": "命中章节关键概念"}
    ]
