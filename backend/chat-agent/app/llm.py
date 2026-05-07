from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator

from openai import AsyncOpenAI

from .config import Settings
from .models import ChatMessage


class ChatResponder:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client = (
            AsyncOpenAI(api_key=settings.deepseek_api_key, base_url=settings.deepseek_base_url)
            if settings.deepseek_api_key
            else None
        )

    async def stream_reply(self, history: list[ChatMessage], message: str, context_prompt: str = "") -> AsyncIterator[str]:
        if self._client is not None:
            async for chunk in self._stream_llm_reply(history, message, context_prompt):
                yield chunk
            return

        if not self._settings.fallback_enabled:
            yield "AI 服务尚未配置，请稍后再试。"
            return

        context_line = f"\n\n当前教学上下文：\n{context_prompt}" if context_prompt else ""
        fallback = (
            "我已经收到你的问题。当前 chat-agent 已接入平台会话链路，"
            "但还没有配置 DeepSeek API Key，所以先返回本地占位回复。"
            f"{context_line}\n\n你的问题：{message}"
        )
        for part in _split_text(fallback, 16):
            await asyncio.sleep(0.02)
            yield part

    async def _stream_llm_reply(self, history: list[ChatMessage], message: str, context_prompt: str) -> AsyncIterator[str]:
        system_prompt = (
            "你是 CloudTeachingAI 的教学助手。回答应清晰、友好、准确，"
            "优先帮助学生理解知识点，也可以协助教师设计教学活动。"
        )
        if context_prompt:
            system_prompt += (
                "\n\n当前对话带有教学上下文。回答时优先围绕这些课程、资源和知识点展开；"
                "如果上下文不足，先说明依据有限，再给出可执行的学习或教学建议。\n"
                f"{context_prompt}"
            )
        messages = [
            {
                "role": "system",
                "content": system_prompt,
            }
        ]
        messages.extend(
            {"role": item.role, "content": item.content}
            for item in history[-12:]
            if item.content.strip()
        )
        messages.append({"role": "user", "content": message})

        stream = await self._client.chat.completions.create(
            model=self._settings.deepseek_model,
            messages=messages,
            temperature=0.3,
            stream=True,
        )
        async for event in stream:
            content = event.choices[0].delta.content
            if content:
                yield content


def _split_text(value: str, size: int) -> list[str]:
    return [value[index:index + size] for index in range(0, len(value), size)]
