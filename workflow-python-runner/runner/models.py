import json
import math
import re
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator

_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
_UNSAFE_JSON_KEYS = {"__proto__", "prototype", "constructor"}


def _validate_json(value: Any, max_depth: int, depth: int = 0) -> None:
    if depth > max_depth:
        raise ValueError("JSON nesting exceeds configured limit")
    if value is None or isinstance(value, (str, bool, int)):
        return
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("non-finite numbers are not valid JSON")
        return
    if isinstance(value, list):
        for item in value:
            _validate_json(item, max_depth, depth + 1)
        return
    if isinstance(value, dict):
        if any(not isinstance(key, str) for key in value):
            raise ValueError("JSON object keys must be strings")
        if any(key in _UNSAFE_JSON_KEYS for key in value):
            raise ValueError("JSON object contains an unsafe key")
        for item in value.values():
            _validate_json(item, max_depth, depth + 1)
        return
    raise ValueError("value is not JSON serializable")


class ExecuteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    runId: str = Field(min_length=1, max_length=128)
    nodeId: str = Field(min_length=1, max_length=128)
    script: str = Field(min_length=1)
    inputs: dict[str, Any]
    timeoutMs: int | None = Field(default=None, ge=1, le=300_000)

    @field_validator("runId", "nodeId")
    @classmethod
    def safe_identifiers(cls, value: str):
        if not _ID.fullmatch(value):
            raise ValueError("identifier contains unsupported characters")
        return value


class ExecuteResponse(BaseModel):
    outputs: dict[str, Any]


class RunnerError(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    error: RunnerError


def validate_json_object(value: Any, max_depth: int) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError("result must be a JSON object")
    _validate_json(value, max_depth)
    return value


def validate_json_value(value: Any, max_depth: int) -> None:
    _validate_json(value, max_depth)


def encode_outputs(value: dict[str, Any], max_depth: int, max_bytes: int) -> bytes:
    _validate_json(value, max_depth)
    encoded = json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")
    if len(encoded) > max_bytes:
        raise ValueError("output exceeds configured limit")
    return encoded
