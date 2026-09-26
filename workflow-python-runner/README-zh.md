# v5ai Workflow Python Runner

[English](README.md)

本目录包含经过认证的 FastAPI 控制面，以及固定的一次性 Python 执行器镜像。Runner 不会在 API 进程中执行工作流代码。每次执行都会启动一个全新的容器，并启用无网络、只读根文件系统、非 root 用户、移除 Linux capabilities、`no-new-privileges`、受限的 CPU/内存/PID/tmpfs/日志，以及运行时默认 seccomp 配置等安全措施。

## 开发与测试

使用 Python 3.12 并安装测试依赖：

```bash
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
python -m pytest -q
```

API 必须配置一个至少 32 个字符的随机 Bearer Token。未配置执行器镜像或没有可用的容器运行时，API 将保持未就绪状态；系统不会回退到本地进程执行。

## 构建不可变的执行器镜像

请在可信构建环境中构建、扫描并发布镜像，然后为 Runner 配置镜像仓库摘要（digest），不要配置可变标签：

```bash
docker build -f workflow-python-runner/images/python-executor/Dockerfile \
  -t registry.example.invalid/v5ai/python-executor:2026-09-26 \
  workflow-python-runner/images/python-executor
docker push registry.example.invalid/v5ai/python-executor:2026-09-26
docker inspect --format='{{index .RepoDigests 0}}' registry.example.invalid/v5ai/python-executor:2026-09-26
```

将得到的 `...@sha256:...` 值设置为 `V5AI_PYTHON_RUNNER_EXECUTOR_IMAGE`。不要在此镜像中放入凭据或用户提供的依赖。重新构建的镜像属于新的制品，需要重新审核并使用新的 digest。

## 生产环境

生产环境必须使用专用 Linux 执行主机，安装 gVisor `runsc`，并通过受双向 TLS 保护的私有 Docker Engine API 端点访问容器运行时；网络和防火墙规则应确保只有 Runner 控制面可以访问该端点。严禁将 Docker/Podman socket 挂载到 Runner API 容器中。将证书文件以只读方式挂载到 `/run/runtime-certs`，并设置 `V5AI_PYTHON_RUNNER_RUNTIME_ENDPOINT=tcp://<private-host>:2376`。Runner 会在执行前检查端点访问能力、镜像是否可用，以及配置的 gVisor runtime。缺少任一前置条件时，`/health` 将返回 503。

Compose 配置、资源限制、运维、回滚、故障排查和 Linux 隔离验收清单，请参阅 [`docs/deploy/workflow-python-runner.md`](../docs/deploy/workflow-python-runner.md)。Compose 只负责 API 接线；Docker Desktop/macOS 和普通 `runc` 不能作为生产隔离能力的验证环境。
