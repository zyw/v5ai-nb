# v5ai Workflow Python Runner

[简体中文](README-zh.md)

This directory contains the authenticated FastAPI control plane and the fixed one-shot Python executor image. The Runner never evaluates workflow code inside its API process. Each execution is a fresh container with no network, a read-only root filesystem, non-root UID, dropped capabilities, `no-new-privileges`, bounded CPU/memory/PIDs/tmpfs/logs, and the runtime's default seccomp profile.

## Development and tests

Use Python 3.12 and install the test extra:

```bash
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
python -m pytest -q
```

The API requires a random bearer token of at least 32 characters. Starting the API without a configured executor image or usable runtime remains unready; there is no local-process fallback.

## Build immutable executor image

Build on a trusted builder, scan and publish the image, then configure the Runner with the registry digest (not a mutable tag):

```bash
docker build -f workflow-python-runner/images/python-executor/Dockerfile \
  -t registry.example.invalid/v5ai/python-executor:2026-09-26 \
  workflow-python-runner/images/python-executor
docker push registry.example.invalid/v5ai/python-executor:2026-09-26
docker inspect --format='{{index .RepoDigests 0}}' registry.example.invalid/v5ai/python-executor:2026-09-26
```

Set the resulting `...@sha256:...` value as `V5AI_PYTHON_RUNNER_EXECUTOR_IMAGE`. Do not put credentials or user-provided dependencies in this image. A rebuilt image is a new reviewed artifact and digest.

## Production

Production requires a dedicated Linux execution host with gVisor `runsc`, a private Docker Engine API endpoint protected by mutual TLS, and network/firewall rules allowing only the Runner control plane to reach that endpoint. Never mount a Docker/Podman socket into the Runner API container. Configure the certificate files read-only at `/run/runtime-certs` and use `V5AI_PYTHON_RUNNER_RUNTIME_ENDPOINT=tcp://<private-host>:2376`; the Runner checks endpoint access, image availability, and the configured gVisor runtime before execution. Missing prerequisites make `/health` return 503.

See [`docs/deploy/workflow-python-runner.md`](../docs/deploy/workflow-python-runner.md) for Compose setup, limits, operations, rollback, troubleshooting, and the Linux isolation acceptance checklist. Compose provides the API wiring only; Docker Desktop/macOS and ordinary `runc` are not production isolation validation.
