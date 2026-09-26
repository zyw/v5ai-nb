import asyncio
import io
import json
import tarfile
import threading
import time
from unittest.mock import MagicMock, call, patch

import pytest

from runner.config import RunnerSettings
from runner.errors import BackendUnavailable, ExecutionFailed, ExecutionTimedOut
from runner.models import ExecuteRequest
from runner.sandbox import OciSandboxBackend


def settings(**kwargs):
    return RunnerSettings(api_token="runner-test-token-value-1234567890", executor_image="registry/runner@sha256:" + "a" * 64, **kwargs)


def test_production_readiness_requires_pinned_image_and_gvisor_runtime():
    client = MagicMock()
    client.info.return_value = {"Runtimes": {"runc": {}}}
    backend = OciSandboxBackend(settings(), client)

    assert asyncio.run(backend.health()) is False


def test_execution_creates_hardened_container_transfers_files_and_cleans_up():
    client = MagicMock()
    client.info.return_value = {"Runtimes": {"runsc": {}}}
    container = MagicMock()
    container.id = "container-123"
    container.wait.return_value = {"StatusCode": 0}
    result_tar = io.BytesIO()
    with tarfile.open(fileobj=result_tar, mode="w") as archive:
        payload = b'{"answer":42}'
        info = tarfile.TarInfo("result.json")
        info.size = len(payload)
        archive.addfile(info, io.BytesIO(payload))
    container.get_archive.return_value = ([result_tar.getvalue()], {})
    client.containers.create.return_value = container
    backend = OciSandboxBackend(settings(), client)
    request = ExecuteRequest(runId="run-1", nodeId="py-1", script="result = {'answer': 42}", inputs={})

    with patch.object(backend, "_probe_runtime", return_value=True):
        result = asyncio.run(backend.execute(request, 2))

    assert result == {"answer": 42}
    options = client.containers.create.call_args.kwargs
    assert options["image"] == settings().executor_image
    assert options["runtime"] == "runsc"
    assert options["network_mode"] == "none"
    assert options["read_only"] is True
    assert options["user"] == "65532:65532"
    assert options["cap_drop"] == ["ALL"]
    assert "no-new-privileges:true" in options["security_opt"]
    assert options["pids_limit"] == settings().pids_limit
    assert options["mem_limit"] == settings().memory_limit_bytes
    assert options["nano_cpus"] == int(settings().cpu_limit * 1_000_000_000)
    assert options["command"] == ["2", "/opt/runner/entrypoint.py", "/work/script.py", "/work/input.json", "/work/result.json", "1048576"]
    assert options["tmpfs"]["/work"].startswith("rw,noexec,nosuid,nodev,size=")
    assert options["ulimits"][0].name == "core"
    assert options["log_config"]["config"]["max-file"] == "1"
    container.put_archive.assert_called_once()
    container.stop.assert_called_once()
    container.remove.assert_called_once_with(force=True)
    assert container.mock_calls.index(call.start()) < container.mock_calls.index(call.put_archive("/work", container.put_archive.call_args.args[1]))
    archive_stream = io.BytesIO(container.put_archive.call_args.args[1])
    with tarfile.open(fileobj=archive_stream, mode="r") as archive:
        files = {entry.name: entry for entry in archive.getmembers()}
    assert set(files) == {"script.py", "input.json", ".ready"}
    assert files["script.py"].mode == 0o444 and files["script.py"].uid == 0
    assert files["input.json"].mode == 0o444 and files["input.json"].uid == 0


def test_missing_gvisor_refuses_production_execution():
    client = MagicMock()
    client.info.return_value = {"Runtimes": {"runc": {}}}
    backend = OciSandboxBackend(settings(), client)
    request = ExecuteRequest(runId="run-1", nodeId="py-1", script="result = {}", inputs={})

    with pytest.raises(BackendUnavailable):
        asyncio.run(backend.execute(request, 1))
    client.containers.create.assert_not_called()


def test_production_probe_uses_fixed_unprivileged_gvisor_container():
    client = MagicMock()
    probe = MagicMock()
    probe.wait.return_value = {"StatusCode": 0}
    client.containers.create.return_value = probe
    backend = OciSandboxBackend(settings(), client)

    assert backend._probe_runtime() is True

    options = client.containers.create.call_args.kwargs
    assert options["entrypoint"] == ["python", "-c", "pass"]
    assert options["runtime"] == "runsc"
    assert options["network_mode"] == "none"
    assert options["read_only"] is True
    assert options["user"] == "65532:65532"
    assert options["cap_drop"] == ["ALL"]
    assert "no-new-privileges:true" in options["security_opt"]
    probe.remove.assert_called_once_with(force=True)


def test_nonzero_exit_is_reported_and_container_is_removed():
    client = MagicMock()
    client.info.return_value = {"Runtimes": {"runsc": {}}}
    container = MagicMock()
    container.id = "failed-container"
    container.wait.return_value = {"StatusCode": 1}
    client.containers.create.return_value = container
    backend = OciSandboxBackend(settings(), client)
    request = ExecuteRequest(runId="run-1", nodeId="py-1", script="raise RuntimeError()", inputs={})

    with patch.object(backend, "_probe_runtime", return_value=True), pytest.raises(ExecutionFailed):
        asyncio.run(backend.execute(request, 2))

    container.stop.assert_called_once_with(timeout=1)
    container.remove.assert_called_once_with(force=True)


def test_timeout_stops_and_removes_container():
    client = MagicMock()
    client.info.return_value = {"Runtimes": {"runsc": {}}}
    container = MagicMock()
    container.id = "timed-out-container"
    container.wait.side_effect = lambda: (time.sleep(0.1) or {"StatusCode": 0})
    client.containers.create.return_value = container
    backend = OciSandboxBackend(settings(), client)
    request = ExecuteRequest(runId="run-1", nodeId="py-1", script="while True: pass", inputs={})

    with patch.object(backend, "_probe_runtime", return_value=True), pytest.raises(ExecutionTimedOut):
        asyncio.run(backend.execute(request, 0.01))

    container.stop.assert_called_once_with(timeout=1)
    container.remove.assert_called_once_with(force=True)


def test_cancellation_cleans_up_container():
    client = MagicMock()
    client.info.return_value = {"Runtimes": {"runsc": {}}}
    container = MagicMock()
    container.id = "cancelled-container"
    entered_wait, finish_wait = threading.Event(), threading.Event()

    def blocked_wait():
        entered_wait.set()
        finish_wait.wait(timeout=1)
        return {"StatusCode": 0}

    container.wait.side_effect = blocked_wait
    client.containers.create.return_value = container
    backend = OciSandboxBackend(settings(), client)
    request = ExecuteRequest(runId="run-1", nodeId="py-1", script="result = {}", inputs={})

    async def cancel_run():
        with patch.object(backend, "_probe_runtime", return_value=True):
            task = asyncio.create_task(backend.execute(request, 10))
            assert await asyncio.to_thread(entered_wait.wait, 1)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
            finish_wait.set()

    asyncio.run(cancel_run())
    container.stop.assert_called_once_with(timeout=1)
    container.remove.assert_called_once_with(force=True)
