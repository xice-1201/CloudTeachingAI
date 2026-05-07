from __future__ import annotations

import base64
import hashlib
import json
from typing import Annotated

from fastapi import Header, Query


def _decode_jwt_payload(token: str) -> dict[str, object]:
    parts = token.split(".")
    if len(parts) < 2:
        return {}
    payload = parts[1]
    padding = "=" * (-len(payload) % 4)
    try:
        raw = base64.urlsafe_b64decode(payload + padding)
        decoded = json.loads(raw)
        return decoded if isinstance(decoded, dict) else {}
    except Exception:
        return {}


def _read_bearer(value: str | None) -> str | None:
    if not value:
        return None
    if value.lower().startswith("bearer "):
        return value[7:].strip()
    return value.strip()


def resolve_user_id(
    authorization_header: Annotated[str | None, Header(alias="Authorization")] = None,
    authorization_query: Annotated[str | None, Query(alias="Authorization")] = None,
    user_id_header: Annotated[str | None, Header(alias="X-User-Id")] = None,
    user_id_query: Annotated[str | None, Query(alias="userId")] = None,
) -> int | str:
    token = _read_bearer(authorization_header) or _read_bearer(authorization_query)
    if not token:
        fallback_user_id = user_id_header or user_id_query
        if fallback_user_id:
            return int(fallback_user_id) if fallback_user_id.isdigit() else fallback_user_id
        return "anonymous"

    payload = _decode_jwt_payload(token)
    for key in ("userId", "user_id", "uid", "sub"):
        value = payload.get(key)
        if isinstance(value, int):
            return value
        if isinstance(value, str) and value:
            return int(value) if value.isdigit() else value

    digest = hashlib.sha256(token.encode("utf-8")).hexdigest()[:16]
    return f"token:{digest}"
