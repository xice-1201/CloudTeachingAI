from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(slots=True)
class Settings:
    deepseek_api_key: str | None = os.getenv("DEEPSEEK_API_KEY")
    deepseek_base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    deepseek_model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    fallback_enabled: bool = os.getenv("CHAT_FALLBACK_ENABLED", "true").lower() == "true"
    course_service_base_url: str = os.getenv("COURSE_SERVICE_BASE_URL", "http://course-service:8003")
    course_context_timeout_seconds: float = float(os.getenv("COURSE_CONTEXT_TIMEOUT_SECONDS", "3"))


settings = Settings()
