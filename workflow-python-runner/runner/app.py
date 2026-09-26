import asyncio
import hmac
import json
import logging
from contextlib import asynccontextmanager
from typing import Any, Protocol

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, PlainTextResponse
from pydantic import ValidationError

from runner.admission import AdmissionGate
from runner.config import RunnerSettings
from runner.errors import BackendUnavailable, ExecutionFailed, ExecutionTimedOut, RunnerFailure
from runner.models import ExecuteRequest, encode_outputs, validate_json_object, validate_json_value
from runner.reaper import OrphanReaper

logger = logging.getLogger("runner")


class SandboxBackend(Protocol):
    async def health(self) -> bool: ...
    async def execute(self, request: ExecuteRequest, timeout_seconds: int) -> dict[str, Any]: ...
    async def close(self) -> None: ...


class _UnavailableBackend:
    async def health(self) -> bool:
        return False

    async def execute(self, request: ExecuteRequest, timeout_seconds: int) -> dict[str, Any]:
        raise BackendUnavailable()

    async def close(self) -> None:
        return None


def _error(code: str, message: str, status: int) -> JSONResponse:
    return JSONResponse(status_code=status, content={"error": {"code": code, "message": message}})


def _depth_guard(value: Any, max_depth: int, depth: int = 0) -> None:
    if depth > max_depth:
        raise ValueError("JSON nesting exceeds configured limit")
    if isinstance(value, dict):
        for item in value.values():
            _depth_guard(item, max_depth, depth + 1)
    elif isinstance(value, list):
        for item in value:
            _depth_guard(item, max_depth, depth + 1)


def create_app(settings: RunnerSettings, backend: SandboxBackend | None = None) -> FastAPI:
    sandbox = backend or _UnavailableBackend()
    gate = AdmissionGate(settings.max_concurrent_executions)
    metrics = {"completed": 0, "failed": 0, "timed_out": 0, "rejected": 0}
    accepting = True
    stop_reaper = asyncio.Event()
    reaper_task = None

    async def on_startup():
        nonlocal reaper_task
        runtime_client = getattr(sandbox, "client", None)
        if runtime_client is not None:
            reaper = OrphanReaper(runtime_client, settings.orphan_ttl_seconds)
            await reaper.reap_once()
            reaper_task = asyncio.create_task(reaper.run(stop_reaper))

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        await on_startup()
        yield
        nonlocal accepting
        accepting = False
        stop_reaper.set()
        if reaper_task is not None:
            await reaper_task
        await sandbox.close()

    app = FastAPI(title="v5ai Workflow Python Runner", docs_url=None, redoc_url=None, lifespan=lifespan)
    app.state.admission = gate
    app.state.backend = sandbox

    @app.middleware("http")
    async def bounded_body(request: Request, call_next):
        if request.url.path != "/execute" or request.method != "POST":
            return await call_next(request)
        content_length = request.headers.get("content-length")
        if content_length:
            try:
                if int(content_length) > settings.max_request_bytes:
                    return _error("REQUEST_TOO_LARGE", "Request body exceeds configured limit", 413)
            except ValueError:
                return _error("INVALID_REQUEST", "Request body is invalid", 400)
        body_parts = bytearray()
        async for part in request.stream():
            body_parts.extend(part)
            if len(body_parts) > settings.max_request_bytes:
                return _error("REQUEST_TOO_LARGE", "Request body exceeds configured limit", 413)
        body = bytes(body_parts)

        request._body = body
        return await call_next(request)

    async def authenticate(request: Request) -> bool:
        supplied = request.headers.get("authorization", "")
        expected = f"Bearer {settings.api_token.get_secret_value()}"
        return hmac.compare_digest(supplied.encode(), expected.encode())

    async def backend_ready() -> bool:
        try:
            return await asyncio.wait_for(sandbox.health(), timeout=6)
        except Exception:
            return False

    @app.get("/health")
    async def health(request: Request):
        if not await authenticate(request):
            return _error("UNAUTHORIZED", "Runner authentication failed", 401)
        if not await backend_ready():
            return _error("BACKEND_UNAVAILABLE", "Execution backend is unavailable", 503)
        if not settings.production_mode:
            return {"status": "ready", "securityTier": "non-production"}
        return {"status": "ready"}

    @app.get("/metrics", response_class=PlainTextResponse)
    async def metrics_endpoint(request: Request):
        if not await authenticate(request):
            return _error("UNAUTHORIZED", "Runner authentication failed", 401)
        return PlainTextResponse(
            "\n".join(
                [
                    "# TYPE v5ai_python_runner_active gauge",
                    f"v5ai_python_runner_active {gate.active}",
                    f"v5ai_python_runner_capacity {gate.limit}",
                    "# TYPE v5ai_python_runner_executions_total counter",
                    *[f'v5ai_python_runner_executions_total{{outcome="{key}"}} {value}' for key, value in metrics.items()],
                ]
            ) + "\n"
        )

    @app.post("/execute")
    async def execute(request: Request):
        if not await authenticate(request):
            return _error("UNAUTHORIZED", "Runner authentication failed", 401)
        if not accepting:
            return _error("BACKEND_UNAVAILABLE", "Runner is shutting down", 503)
        try:
            raw = await request.json()
            _depth_guard(raw, settings.max_json_depth)
            command = ExecuteRequest.model_validate(raw)
            validate_json_value(command.inputs, settings.max_json_depth)
        except (ValueError, json.JSONDecodeError, ValidationError, UnicodeDecodeError):
            return _error("INVALID_REQUEST", "Request payload is invalid", 400)
        if len(command.script.encode("utf-8")) > settings.max_script_bytes:
            return _error("SCRIPT_TOO_LARGE", "Python script exceeds configured limit", 413)
        if not await backend_ready():
            return _error("BACKEND_UNAVAILABLE", "Execution backend is unavailable", 503)
        if not await gate.try_acquire():
            metrics["rejected"] += 1
            return _error("CAPACITY_EXCEEDED", "Runner is at execution capacity", 429)
        timeout_seconds = min(
            command.timeoutMs / 1000 if command.timeoutMs is not None else settings.default_execution_seconds,
            settings.max_execution_seconds,
        )
        try:
            outputs = await asyncio.wait_for(sandbox.execute(command, timeout_seconds), timeout=timeout_seconds + 5)
            outputs = validate_json_object(outputs, settings.max_json_depth)
            encode_outputs(outputs, settings.max_json_depth, settings.max_output_bytes)
            metrics["completed"] += 1
            return {"outputs": outputs}
        except asyncio.TimeoutError:
            metrics["timed_out"] += 1
            return _error("EXECUTION_TIMEOUT", "Python execution exceeded its time limit", 504)
        except RunnerFailure as exc:
            metrics["timed_out" if exc.status_code == 504 else "failed"] += 1
            return _error(exc.code, exc.message, exc.status_code)
        except (ValueError, TypeError, OverflowError):
            metrics["failed"] += 1
            return _error("INVALID_OUTPUT", "Python execution returned invalid output", 422)
        except asyncio.CancelledError:
            raise
        except Exception:
            metrics["failed"] += 1
            logger.exception("python_execution_failed run_id=%s node_id=%s", command.runId, command.nodeId)
            return _error("EXECUTION_FAILED", "Python execution failed", 422)
        finally:
            await gate.release()

    return app
