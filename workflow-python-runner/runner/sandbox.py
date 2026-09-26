import asyncio
import io
import json
import logging
import math
import tarfile
import time
import uuid
from typing import Any

import docker
from docker.errors import DockerException, ImageNotFound, NotFound

from runner.config import RunnerSettings
from runner.errors import BackendUnavailable, ExecutionFailed, ExecutionTimedOut
from runner.models import ExecuteRequest, encode_outputs

logger = logging.getLogger("runner.sandbox")
LABEL_KEY = "v5ai.workflow-python-runner.managed"


def _archive(files: dict[str, bytes], modes: dict[str, int] | None = None) -> bytes:
    target = io.BytesIO()
    with tarfile.open(fileobj=target, mode="w") as archive:
        for name, content in files.items():
            entry = tarfile.TarInfo(name)
            entry.size = len(content)
            entry.mode = (modes or {}).get(name, 0o444)
            entry.uid = entry.gid = 0
            entry.uname = entry.gname = "root"
            archive.addfile(entry, io.BytesIO(content))
    return target.getvalue()


class OciSandboxBackend:
    def __init__(self, settings: RunnerSettings, client=None):
        self.settings = settings
        self.client = client or docker.DockerClient(
            base_url=settings.runtime_endpoint,
            timeout=settings.max_execution_seconds + 15,
            tls=(
                docker.tls.TLSConfig(
                    client_cert=(settings.runtime_client_cert, settings.runtime_client_key),
                    ca_cert=settings.runtime_ca_cert,
                    verify=True,
                )
                if settings.runtime_endpoint.startswith(("tcp://", "https://"))
                else None
            ),
        )
        self._closing = False
        self._health_cache_until = 0.0
        self._health_cache_value = False

    async def health(self) -> bool:
        if time.monotonic() < self._health_cache_until:
            return self._health_cache_value
        if self._closing or not self.settings.executor_image:
            return False
        if self.settings.production_mode and "@sha256:" not in self.settings.executor_image:
            return False
        try:
            info = await asyncio.to_thread(self.client.info)
            runtimes = info.get("Runtimes", {})
            runtime_ready = not self.settings.production_mode or self.settings.runtime_name in runtimes
            await asyncio.to_thread(self.client.images.get, self.settings.executor_image)
            if runtime_ready and self.settings.production_mode:
                runtime_ready = await asyncio.to_thread(self._probe_runtime)
            self._health_cache_value = bool(runtime_ready)
            self._health_cache_until = time.monotonic() + 5
            return self._health_cache_value
        except (DockerException, OSError, KeyError, TypeError):
            self._health_cache_value = False
            self._health_cache_until = time.monotonic() + 2
        return False

    def _probe_runtime(self) -> bool:
        probe = None
        try:
            probe = self.client.containers.create(
                image=self.settings.executor_image,
                entrypoint=["python", "-c", "pass"],
                command=[],
                name=f"v5ai-python-probe-{uuid.uuid4().hex}",
                labels={LABEL_KEY: "true", "v5ai.workflow-python-runner.created-at": str(int(time.time()))},
                user="65532:65532",
                runtime=self.settings.runtime_name,
                network_mode="none",
                read_only=True,
                cap_drop=["ALL"],
                security_opt=["no-new-privileges:true"],
                pids_limit=self.settings.pids_limit,
                mem_limit=self.settings.memory_limit_bytes,
                nano_cpus=int(self.settings.cpu_limit * 1_000_000_000),
                tmpfs={"/tmp": "rw,noexec,nosuid,nodev,size=1048576"},
                detach=True,
            )
            probe.start()
            return probe.wait(timeout=2).get("StatusCode") == 0
        except (DockerException, OSError, AttributeError, TypeError):
            return False
        finally:
            if probe is not None:
                try:
                    probe.remove(force=True)
                except (DockerException, OSError):
                    pass

    @staticmethod
    async def _uncancellable_call(method, *args, **kwargs):
        task = asyncio.create_task(asyncio.to_thread(method, *args, **kwargs))
        try:
            return await asyncio.shield(task)
        except asyncio.CancelledError:
            try:
                await task
            finally:
                raise

    async def execute(self, request: ExecuteRequest, timeout_seconds: int) -> dict[str, Any]:
        if not await self.health():
            raise BackendUnavailable()
        container_id = None
        try:
            encoded_inputs = json.dumps(request.inputs, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")
            files = _archive({"script.py": request.script.encode("utf-8"), "input.json": encoded_inputs, ".ready": b"ready"})
            config = self.settings
            labels = {LABEL_KEY: "true", "v5ai.workflow-python-runner.created-at": str(int(time.time()))}
            spec = {
                "image": config.executor_image,
                "runtime": config.runtime_name if config.production_mode else None,
                "command": [
                    str(max(1, math.ceil(timeout_seconds))),
                    "/opt/runner/entrypoint.py",
                    "/work/script.py",
                    "/work/input.json",
                    "/work/result.json",
                    str(config.max_output_bytes),
                ],
                "name": f"v5ai-python-{uuid.uuid4().hex}",
                "labels": labels,
                "user": "65532:65532",
                "network_mode": "none",
                "read_only": True,
                "cap_drop": ["ALL"],
                "security_opt": ["no-new-privileges:true"],
                "pids_limit": config.pids_limit,
                "mem_limit": config.memory_limit_bytes,
                "nano_cpus": int(config.cpu_limit * 1_000_000_000),
                "ulimits": [
                    docker.types.Ulimit(name="core", soft=0, hard=0),
                    docker.types.Ulimit(name="nofile", soft=64, hard=64),
                    docker.types.Ulimit(name="fsize", soft=config.workspace_limit_bytes, hard=config.workspace_limit_bytes),
                ],
                "tmpfs": {"/work": f"rw,noexec,nosuid,nodev,size={config.workspace_limit_bytes},mode=1777"},
                "log_config": {"type": "json-file", "config": {"max-size": f"{config.log_limit_bytes}b", "max-file": "1"}},
                "environment": {
                    "PYTHONDONTWRITEBYTECODE": "1",
                    "PYTHONUNBUFFERED": "1",
                    "RESULT_MAX_BYTES": str(config.max_output_bytes),
                    "V5AI_RUNNER_WAIT_FOR_INPUTS": "1",
                },
                "detach": True,
                "stdin_open": False,
                "tty": False,
                "auto_remove": False,
            }
            if spec["runtime"] is None:
                del spec["runtime"]
            creation = asyncio.create_task(asyncio.to_thread(self.client.containers.create, **spec))
            try:
                container = await asyncio.shield(creation)
            except asyncio.CancelledError:
                container = await creation
                container_id = container.id
                raise
            container_id = container.id
            await self._uncancellable_call(container.start)
            await self._uncancellable_call(container.put_archive, "/work", files)
            try:
                status = await asyncio.wait_for(asyncio.to_thread(container.wait), timeout=timeout_seconds)
            except asyncio.TimeoutError as exc:
                raise ExecutionTimedOut() from exc
            if status.get("StatusCode") in (124, 137):
                raise ExecutionTimedOut()
            if status.get("StatusCode") != 0:
                raise ExecutionFailed()
            stream, _ = await self._uncancellable_call(container.get_archive, "/work/result.json")
            result_bytes = await asyncio.to_thread(self._read_archive_file, stream, "result.json", config.max_output_bytes)
            parsed = json.loads(result_bytes)
            if not isinstance(parsed, dict):
                raise ExecutionFailed()
            encode_outputs(parsed, config.max_json_depth, config.max_output_bytes)
            return parsed
        except asyncio.CancelledError:
            raise
        except (BackendUnavailable, ExecutionFailed, ExecutionTimedOut):
            raise
        except (DockerException, ImageNotFound, NotFound, OSError, ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
            logger.warning("sandbox_operation_failed error_type=%s", type(exc).__name__)
            raise ExecutionFailed() from exc
        finally:
            if container_id:
                await self._cleanup(container if "container" in locals() else None, container_id)

    @staticmethod
    def _read_archive_file(chunks, name: str, limit: int) -> bytes:
        payload_buffer = bytearray()
        archive_limit = limit + 1024 * 1024
        try:
            for chunk in chunks:
                payload_buffer.extend(chunk)
                if len(payload_buffer) > archive_limit:
                    raise ValueError("result archive exceeds configured limit")
        finally:
            close = getattr(chunks, "close", None)
            if close:
                close()
        payload = bytes(payload_buffer)
        with tarfile.open(fileobj=io.BytesIO(payload), mode="r:*") as archive:
            member = archive.getmember(name)
            if not member.isfile() or member.size > limit:
                raise ValueError("result file exceeds configured limit")
            handle = archive.extractfile(member)
            if handle is None:
                raise ValueError("result file is unavailable")
            data = handle.read(limit + 1)
            if len(data) > limit:
                raise ValueError("result file exceeds configured limit")
            return data

    async def _cleanup(self, container, container_id: str) -> None:
        try:
            if container is not None:
                await asyncio.to_thread(container.stop, timeout=1)
        except (DockerException, OSError):
            pass
        try:
            if container is not None:
                await asyncio.to_thread(container.remove, force=True)
        except (DockerException, OSError):
            logger.warning("sandbox_cleanup_failed container_id_hash=%s", uuid.uuid5(uuid.NAMESPACE_OID, container_id).hex[:12])

    async def close(self) -> None:
        self._closing = True
        await asyncio.to_thread(self.client.close)
