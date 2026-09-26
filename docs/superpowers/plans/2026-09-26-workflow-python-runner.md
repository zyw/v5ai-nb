# Workflow Python Runner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a separately deployable authenticated Python Runner that executes workflow scripts only in bounded one-shot OCI sandboxes, plus compatible Java, UI, configuration, and deployment support.

**Architecture:** A FastAPI control plane validates/authenticates requests and admits bounded work, then delegates each script to a `SandboxBackend`. The OCI backend uses a fixed image and fixed security/resource settings; production readiness requires gVisor, while development mode is explicitly non-production. v5ai-nb remains fail-closed and calls the existing synchronous `/health` and `/execute` contract.

**Tech Stack:** Python 3, FastAPI, Pydantic, pytest; OCI runtime API; Java 21/Spring Boot; Vue 3; Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-26-workflow-python-runner-design.md`

## Global Constraints

- A request may provide only `runId`, `nodeId`, `script`, `inputs`, and bounded timeout; it never selects an image, command, mount, network, or resource limit.
- Each execution receives a fresh workspace and sandbox; all exit paths stop and clean resources.
- Runner default execution networking is disabled; scripts cannot install packages at runtime.
- Production readiness requires a fixed image digest and working gVisor runtime; development mode is never reported production-ready.
- Limits are enforced server-side: 30-second default execution time, 64 KiB script, 1 MiB request and output, four concurrent executions; clients cannot raise server maxima.
- HTTP contract: authenticated `GET /health`, `POST /execute`; stable 400/401/413/422/429/503/504 errors, with no secrets, stack traces, or host paths in responses.
- Preserve Java workflow compatibility (`script` then legacy `code`, context snapshot inputs, `outputs` object, optional `outputVar`) and fail closed without local execution fallback.
- Every new or changed `v5ai.*` setting must be documented in `v5ai-starter/src/main/resources/v5ai-nb-配置文件详情.yml` with default, options, environment mapping, and security/activation notes.
- Do not modify unrelated dirty user files; do not mount a host runtime socket into the Runner API container.

## Review Focus

- Malformed, deeply nested, or oversized JSON requests must be rejected before sandbox allocation.
- Cancellation, timeout, runtime errors, and API shutdown must not leave a runnable container or workspace.
- Concurrent admission must reject promptly at capacity and always release permits after failures.
- User output must reject non-object JSON, NaN/Infinity, oversized values, and unsafe JSON keys without leaking execution details.
- A production Runner with missing digest, missing gVisor, or ineffective security settings must remain unready and reject execution.

---

### Task 1: Runner API contract, configuration, and admission control

**Files:**
- Create: `workflow-python-runner/pyproject.toml`, `workflow-python-runner/runner/config.py`, `workflow-python-runner/runner/models.py`, `workflow-python-runner/runner/errors.py`, `workflow-python-runner/runner/app.py`, `workflow-python-runner/runner/admission.py`
- Test: `workflow-python-runner/tests/test_api.py`, `workflow-python-runner/tests/test_admission.py`

**Interfaces:**
- Produces `POST /execute` request fields `runId`, `nodeId`, `script`, `inputs`, optional `timeoutMs`; success `{ "outputs": object }`; error `{ "error": { "code": string, "message": string } }`.
- Produces `SandboxBackend.health()`, `SandboxBackend.execute(request, timeout_seconds)`, and `SandboxBackend.close()` async interfaces for Task 2.

- [ ] Write tests for bearer auth on both routes, valid and invalid payloads, request/script limits, stable status/error shape, bounded concurrency, and health reflecting backend readiness.
- [ ] Run `cd workflow-python-runner && python -m pytest tests/test_api.py tests/test_admission.py -q`; confirm expected failures before implementation.
- [ ] Implement typed configuration with validated server maxima/defaults and fixed `SandboxBackend` protocol; make API reject unauthenticated/invalid/oversized requests before execution and return stable sanitized errors.
- [ ] Re-run targeted tests and full `python -m pytest -q`; expected all unit tests pass.

### Task 2: One-shot OCI sandbox and fixed Python execution image

**Files:**
- Create: `workflow-python-runner/runner/sandbox.py`, `workflow-python-runner/runner/reaper.py`, `workflow-python-runner/images/python-executor/Dockerfile`, `workflow-python-runner/images/python-executor/entrypoint.py`
- Test: `workflow-python-runner/tests/test_sandbox.py`, `workflow-python-runner/tests/test_executor_entrypoint.py`

**Interfaces:**
- Consumes Task 1 `SandboxBackend` protocol and validated request model.
- Produces OCI backend that accepts only server-side configured runtime endpoint/image digest and never reads execution settings from request fields.

- [ ] Write tests for fixed image/argv, read-only script/input mounts, isolated writable result mount, no network, non-root, read-only root, dropped capabilities, no-new-privileges, seccomp, CPU/memory/PID/disk/log limits, gVisor-required readiness, and cleanup on success/error/timeout/cancel.
- [ ] Run targeted pytest tests and confirm security assertions fail before implementation.
- [ ] Implement structured OCI runtime API invocation using a configured remote/private runtime endpoint (never mount a host socket); production requires gVisor and image digest; development backend must be explicitly non-production.
- [ ] Implement the fixed image entrypoint: load JSON `inputs`, execute `script.py` with `inputs`, require JSON-serializable object `result`, write bounded `result.json`, and cap stdout/stderr.
- [ ] Re-run targeted and full unit suite; expected all tests pass and no runtime artifact remains after each failure path.

### Task 3: Runtime readiness, lifecycle recovery, and observability

**Files:**
- Modify: `workflow-python-runner/runner/app.py`, `workflow-python-runner/runner/sandbox.py`, `workflow-python-runner/runner/reaper.py`
- Test: `workflow-python-runner/tests/test_lifecycle.py`, `workflow-python-runner/tests/test_reaper.py`

**Interfaces:**
- Consumes Task 2 OCI backend.
- Produces readiness that reports only generic ready/unready state, plus bounded execution metrics and orphan cleanup lifecycle.

- [ ] Write tests for probe execution readiness, graceful shutdown admission stop, execution cancellation, orphan container/workspace TTL cleanup, and sanitized metrics/log fields.
- [ ] Run targeted tests; confirm failures exercise the missing lifecycle behavior.
- [ ] Implement bounded readiness probing, graceful shutdown, cleanup/reaper, and execution metrics that exclude script text, input data, tokens, and host paths.
- [ ] Run full Python unit suite; expected all tests pass. Linux gVisor operational acceptance is explicitly deferred to user-side integration.

### Task 4: Runner packaging and two-dialect Compose deployment

**Files:**
- Create: `workflow-python-runner/Dockerfile`, `workflow-python-runner/.dockerignore`
- Modify: `script/docker/docker-compose-postgresql.yml`, `script/docker/docker-compose-mysql.yml`, `.env.example`, deployment docs
- Test: `workflow-python-runner/tests/test_deployment_contract.py`

**Interfaces:**
- Consumes Task 1-3 API/configuration.
- Produces an internal-only Runner service in both Compose variants, with secrets injected from environment and a separately configured sandbox runtime endpoint; no published host port and no runtime socket mount.

- [ ] Write a deployment contract test/check for matching Runner env/service settings across PostgreSQL and MySQL Compose files, internal-only network, healthcheck, and absence of privileged/socket mounts.
- [ ] Run the contract check and confirm it fails before Compose changes.
- [ ] Add Runner API and immutable executor-image build configuration; keep gVisor runtime daemon/endpoint on a separate dedicated Linux execution host and document endpoint/TLS requirements.
- [ ] Add non-secret environment variable names/defaults to `.env.example`; validate Compose syntax for both dialects and re-run deployment contract checks.

### Task 5: Harden Java Runner client and surface stable failures

**Files:**
- Modify: `v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/core/executor/PythonNodeExecutor.java`, `v5ai-starter/src/main/resources/application.yml`, `v5ai-ui/src/api/client.ts`, `v5ai-ui/src/views/WorkflowEditorView.vue`
- Test: `v5ai-modules/v5ai-workflow/src/test/java/xin/v5ai/nb/workflow/core/executor/PythonNodeExecutorTest.java`

**Interfaces:**
- Consumes Runner HTTP contract from Task 1.
- Produces bounded request/response handling, short cached readiness (or removes redundant per-call health request), stable error-code propagation, and preserved legacy script/code/outputVar semantics.

- [ ] Write tests for new `script`/legacy `code`, empty `outputVar`, absent config fail-closed, HTTP status/error-code mapping, response-size cap, request timeout below Runner maximum, and readiness cache behavior.
- [ ] Run the focused Maven test and confirm expected failures before Java changes.
- [ ] Implement bounded HTTP client behavior and parse Runner's stable error object without forwarding internal stack traces; keep no local fallback.
- [ ] Align Python-node helper text with `result` output contract, default 30-second max, preinstalled dependencies, Runner availability, and isolation tier.
- [ ] Run focused test and `mvn -pl v5ai-modules/v5ai-workflow -am -DskipTests package`; expected focused test passes and module build succeeds.

### Task 6: Configuration reference, deployment and operations documentation

**Files:**
- Modify: `v5ai-starter/src/main/resources/v5ai-nb-配置文件详情.yml`, `docs/deploy/dev.md`, `README.md`
- Create: `workflow-python-runner/README.md`, `docs/deploy/workflow-python-runner.md`

**Interfaces:**
- Consumes Runner/Java configuration names finalized in Tasks 1-5.
- Produces complete local/API-only development setup and production Linux gVisor deployment/acceptance guide, clearly identifying the deferred user-side integration checks.

- [ ] Document every new `v5ai.workflow.python.*` key, default, environment variable, empty/fail-closed behavior, max execution time, and token secrecy in the configuration reference.
- [ ] Document Runner API, fixed executor image build/update, private network, remote runtime endpoint TLS, gVisor readiness, resource limits, reaper/metrics, backup-free ephemeral data, upgrade/rollback, and troubleshooting.
- [ ] Validate Python examples and YAML/Compose syntax; run `git diff --check`, full Python unit suite, focused Java tests and frontend build.
- [ ] Record Linux gVisor network/resource/isolation integration acceptance as not performed here, with exact commands/checklist for the deployment owner.

