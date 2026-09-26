import json

from fastapi.testclient import TestClient

from runner.app import create_app
from runner.config import RunnerSettings


class StubBackend:
    def __init__(self, outputs=None, ready=True):
        self.outputs = outputs or {"answer": 42}
        self.ready = ready
        self.requests = []

    async def health(self):
        return self.ready

    async def execute(self, request, timeout_seconds):
        self.requests.append((request, timeout_seconds))
        return self.outputs

    async def close(self):
        return None


def make_client(backend=None, **overrides):
    settings = RunnerSettings(api_token="runner-test-token-value-1234567890", **overrides)
    stub = backend or StubBackend()
    return TestClient(create_app(settings, stub)), stub


def test_health_requires_bearer_token_and_does_not_disclose_backend_details():
    client, _ = make_client()

    denied = client.get("/health")
    ready = client.get("/health", headers={"Authorization": "Bearer runner-test-token-value-1234567890"})

    assert denied.status_code == 401
    assert denied.json() == {"error": {"code": "UNAUTHORIZED", "message": "Runner authentication failed"}}
    assert ready.status_code == 200
    assert ready.json() == {"status": "ready"}


def test_execute_returns_outputs_and_caps_requested_timeout_at_server_limit():
    client, backend = make_client(max_execution_seconds=10)

    response = client.post(
        "/execute",
        headers={"Authorization": "Bearer runner-test-token-value-1234567890"},
        json={"runId": "run-123", "nodeId": "python-node", "script": "result = inputs", "inputs": {"x": 21}, "timeoutMs": 30000},
    )

    assert response.status_code == 200
    assert response.json() == {"outputs": {"answer": 42}}
    assert backend.requests[0][0].inputs == {"x": 21}
    assert backend.requests[0][1] == 10


def test_execute_uses_existing_default_timeout_when_timeout_is_omitted():
    client, backend = make_client(default_execution_seconds=7, max_execution_seconds=10)

    response = client.post(
        "/execute",
        headers={"Authorization": "Bearer runner-test-token-value-1234567890"},
        json={"runId": "run-123", "nodeId": "python-node", "script": "result = inputs", "inputs": {}},
    )

    assert response.status_code == 200
    assert backend.requests[0][1] == 7


def test_execute_rejects_bad_shape_without_allocating_sandbox():
    client, backend = make_client()

    response = client.post(
        "/execute",
        headers={"Authorization": "Bearer runner-test-token-value-1234567890"},
        json={"runId": "../etc/passwd", "nodeId": "node", "script": "result = {}", "inputs": {}, "image": "attacker/image"},
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_REQUEST"
    assert backend.requests == []


def test_execute_rejects_non_finite_and_unsafe_input_json_before_sandbox_allocation():
    client, backend = make_client()
    for inputs in ({"value": float("nan")}, {"constructor": {"prototype": {}}}):
        response = client.post(
            "/execute",
            headers={"Authorization": "Bearer runner-test-token-value-1234567890", "Content-Type": "application/json"},
            content=json.dumps({"runId": "run-1", "nodeId": "node-1", "script": "result = {}", "inputs": inputs}),
        )
        assert response.status_code == 400
        assert response.json()["error"]["code"] == "INVALID_REQUEST"
    assert backend.requests == []


def test_execute_rejects_oversized_body_before_sandbox_allocation():
    client, backend = make_client(max_request_bytes=128)

    response = client.post(
        "/execute",
        headers={"Authorization": "Bearer runner-test-token-value-1234567890", "Content-Type": "application/json"},
        content=b'{"runId":"r","nodeId":"n","script":"' + b"x" * 256 + b'","inputs":{}}',
    )

    assert response.status_code == 413
    assert response.json()["error"]["code"] == "REQUEST_TOO_LARGE"
    assert backend.requests == []


def test_execute_rejects_oversized_script_and_reports_backend_unready():
    client, backend = make_client(max_script_bytes=16)
    too_large = client.post(
        "/execute",
        headers={"Authorization": "Bearer runner-test-token-value-1234567890"},
        json={"runId": "run-1", "nodeId": "node-1", "script": "x" * 17, "inputs": {}},
    )

    unready_client, _ = make_client(backend=StubBackend(ready=False))
    unready = unready_client.get("/health", headers={"Authorization": "Bearer runner-test-token-value-1234567890"})

    assert too_large.status_code == 413
    assert too_large.json()["error"]["code"] == "SCRIPT_TOO_LARGE"
    assert backend.requests == []
    assert unready.status_code == 503
    assert unready.json() == {"error": {"code": "BACKEND_UNAVAILABLE", "message": "Execution backend is unavailable"}}


def test_execute_requires_authentication():
    client, backend = make_client()

    response = client.post("/execute", json={"runId": "run", "nodeId": "node", "script": "result = {}", "inputs": {}})

    assert response.status_code == 401
    assert backend.requests == []


def test_metrics_are_authenticated_and_contain_only_bounded_counters():
    client, backend = make_client()
    auth = {"Authorization": "Bearer runner-test-token-value-1234567890"}

    denied = client.get("/metrics")
    accepted = client.get("/metrics", headers=auth)

    assert denied.status_code == 401
    assert accepted.status_code == 200
    assert "v5ai_python_runner_active 0" in accepted.text
    assert "script" not in accepted.text and "token" not in accepted.text
    assert backend.requests == []
