# Workflow Python Runner 部署与运维

## 架构与安全边界

`v5ai-nb` 通过私有网络调用 Runner 的 `/health` 和 `/execute`。Runner 仅作鉴权、输入校验、并发准入和生命周期控制；用户代码在每次新建、执行后销毁的 OCI 容器中运行。Runner API 不发布宿主端口，工作流不能指定镜像、命令、挂载、网络或资源限制。请求大小/脚本/输出默认各 1 MiB/64 KiB/1 MiB，并发 4，默认与最大运行时 30 秒；CPU 1 核、内存 256 MiB、PID 64、工作 tmpfs 16 MiB、容器日志 64 KiB。所有值由 Runner 服务端强制且有配置上限。

生产要求专用 Linux 节点、gVisor `runsc`、不可变 executor 镜像 digest、互 TLS 保护的私有 Docker Engine API。网络 ACL 只允许 Runner 控制面访问 daemon；daemon 不能访问业务数据库、模型服务和公网。不得把运行时 socket 挂载到 Runner API 容器。Runner 控制面 token、daemon 客户端证书只能通过部署 Secret/受限只读挂载注入，不写入工作流、镜像、日志或仓库。执行镜像内有独立 PID 1 watchdog，API 进程失联后仍会按服务端选定的超时杀掉脚本进程组。

Python 脚本可访问 `inputs`，必须给 `result` 赋一个 JSON 对象。仅使用预装依赖；不支持运行时联网或 `pip install`。执行异常、输出不是 JSON object、过大输出、超时、容量满和后端失效分别返回稳定错误码；Java 端不回退到本地 Python。

## 生产部署步骤

1. 在专用 Linux 执行节点安装并启用受支持版本的 gVisor `runsc`，配置 Docker Engine 仅监听防火墙保护的管理地址，启用 TLS client authentication。禁止公开 2375/2376。
2. 在可信 CI 构建并扫描 executor：

   ```bash
   docker build -f workflow-python-runner/images/python-executor/Dockerfile \
     -t registry.example.invalid/v5ai/python-executor:release workflow-python-runner/images/python-executor
   docker push registry.example.invalid/v5ai/python-executor:release
   docker inspect --format='{{index .RepoDigests 0}}' registry.example.invalid/v5ai/python-executor:release
   ```

   把 digest 形式 `registry/...@sha256:...` 固定给 Runner。
3. 为 Runner 生成至少 32 字符随机 token，并签发 daemon CA、Runner 客户端证书/私钥。私钥权限限定 Runner 服务账号可读。将文件放在 Compose `V5AI_PYTHON_RUNNER_CERT_DIR` 指定目录（默认 `script/docker/secrets/python-runner`），挂载只读；不得提交真实证书。
4. 在 `.env`/密钥管理系统设置以下值。Runner Token 必填且不得使用仓库内的固定默认值；未设置时 Runner 启动校验失败：

   ```dotenv
   V5AI_WORKFLOW_PYTHON_RUNNER_TOKEN=<random-secret>
   V5AI_PYTHON_RUNNER_RUNTIME_ENDPOINT=tcp://<private-daemon-host>:2376
   V5AI_PYTHON_RUNNER_EXECUTOR_IMAGE=registry.example.invalid/v5ai/python-executor@sha256:<digest>
   V5AI_PYTHON_RUNNER_PRODUCTION_MODE=true
   V5AI_PYTHON_RUNNER_RUNTIME_NAME=runsc
   V5AI_PYTHON_RUNNER_RUNTIME_CA_CERT=/run/runtime-certs/ca.pem
   V5AI_PYTHON_RUNNER_RUNTIME_CLIENT_CERT=/run/runtime-certs/client-cert.pem
   V5AI_PYTHON_RUNNER_RUNTIME_CLIENT_KEY=/run/runtime-certs/client-key.pem
   ```

   Runner API 的 TLS 客户端证书对 daemon 做 mTLS。Compose 中 v5ai 仅通过 `python-runner-control` 内部网络访问 API；Runner 另接受防火墙约束的 runtime 网络。API 服务不映射 `ports`，也没有宿主 runtime socket 挂载。readiness 会实际创建并运行一个无网络、非 root、只读 rootfs 的短探针容器，并在完成后移除；探针每 5 秒缓存一次。
5. 选择 PostgreSQL 或 MySQL Compose 启动：

   ```bash
   cd script/docker
   docker compose -f docker-compose-postgresql.yml --env-file ../../.env up -d --build
   # 或 docker-compose-mysql.yml
   docker compose -f docker-compose-postgresql.yml --env-file ../../.env ps
   ```

   PostgreSQL/MySQL 两份 Compose 的 Runner 配置保持一致。确认 `python-runner` 为 healthy；未配置 digest、证书、daemon 或 gVisor 时其 healthcheck 会失败，此时 Java Python 节点 fail-closed。

## 配置项

### v5ai-nb 调用端

完整的 `v5ai.workflow.python.*` 默认值与环境变量映射见 [配置文件详情](../../v5ai-starter/src/main/resources/v5ai-nb-配置文件详情.yml)。核心设置为 Runner URL（默认空，禁用）、Bearer token（默认空）、HTTP 总时限 35 秒、成功 readiness 缓存 10 秒、最大响应体 1 MiB。请求总时限应大于 Runner 执行上限并留启动/清理余量。Token 只通过密钥系统/环境注入。

### Runner 控制面（环境变量前缀 `V5AI_PYTHON_RUNNER_`）

| 变量 | 默认 | 说明 |
|---|---:|---|
| `API_TOKEN` | 必填 | 至少 32 个非空白字符；健康和执行接口共用 |
| `RUNTIME_ENDPOINT` | `unix:///var/run/docker.sock` | 生产使用 TLS daemon TCP endpoint；Compose 不挂 socket，因此默认不会就绪 |
| `RUNTIME_CA_CERT` / `RUNTIME_CLIENT_CERT` / `RUNTIME_CLIENT_KEY` | 空 | TCP endpoint 必须三者齐全，客户端证书只读挂载 |
| `EXECUTOR_IMAGE` | 空 | 生产必须 `@sha256:` digest 固定 |
| `PRODUCTION_MODE` | `true` | 生产时强制 gVisor；false 只能用于无敏感数据的开发，不能视作安全验收 |
| `RUNTIME_NAME` | `runsc` | OCI runtime 名称 |
| `DEFAULT_EXECUTION_SECONDS` / `MAX_EXECUTION_SECONDS` | 30 / 30 | 配置范围 1..300 秒；API timeout 不能突破最大值 |
| `MAX_SCRIPT_BYTES` / `MAX_REQUEST_BYTES` / `MAX_OUTPUT_BYTES` | 65536 / 1048576 / 1048576 | 服务端硬限制，配置上限分别 1 MiB / 8 MiB / 8 MiB |
| `MAX_CONCURRENT_EXECUTIONS` | 4 | 满载快速返回 429，不排队；配置上限 256 |
| `MAX_JSON_DEPTH` | 32 | 输入与输出递归深度上限 |
| `CPU_LIMIT` / `MEMORY_LIMIT_BYTES` / `PIDS_LIMIT` | 1 / 268435456 / 64 | 容器资源上限 |
| `WORKSPACE_LIMIT_BYTES` / `LOG_LIMIT_BYTES` | 16777216 / 65536 | per-container tmpfs 与 Docker 日志轮转上限 |
| `ORPHAN_TTL_SECONDS` | 600 | 启动和定期回收带 Runner 标签且超 TTL 的孤儿容器 |

## 运维、升级与回滚

- `/health` 必须携带 Bearer Token；只有执行后端可用才返回 200。响应不暴露 daemon 地址、宿主路径或镜像信息。`/metrics` 同样需要认证，只给出 active/capacity、完成/失败/超时/拒绝计数，不含脚本、输入、Token 或路径。
- Runner 不持久化工作流数据。执行脚本、输入、结果只在临时容器 tmpfs；镜像内 watchdog 强制执行硬时限，任务结束清理，Runner 启动与周期回收带专用标签的过期容器。不要为 Runner 建立数据备份任务。
- 监控 503 readiness、429 拒绝、504 超时、失败计数、容器创建/清理日志以及 Linux 主机 CPU/内存/PID/磁盘压力。日志仅记录追踪 ID 或错误类型；不要启用 HTTP body 日志。
- 升级先发布并扫描新的 executor digest，测试 gVisor 与限制后滚动更新 `EXECUTOR_IMAGE`。保留上一个 digest 与 Runner 镜像用于回滚；回滚只切换镜像 digest/Runner 版本，不修改工作流数据。
- 故障排查：401 检查两端 token；503 检查 TLS、daemon ACL、镜像 digest 与 `runsc` 注册；429 检查并发和容量；422 检查 `result` 是否 JSON object/脚本异常；504 检查任务耗时与节点 `timeoutMs`。对外错误不包含沙箱堆栈；仅在受控 Runner 主机查看脱敏服务日志。

## Linux gVisor 隔离验收（需部署负责人执行）

本开发环境未执行真实 Linux gVisor/网络逃逸验收。上线前在专用 Linux 节点完成并留存结果：

1. 确认 daemon `docker info` 的 Runtimes 中有 `runsc`；用带认证的 Runner `/health` 验证 API readiness。
2. 运行一条成功的 `result = {'ok': True}` 工作流；确认临时容器启动、输出正确、运行结束后容器消失。
3. 运行超时脚本（例如 `while True: pass`），确认在配置期限后返回 `EXECUTION_TIMEOUT`、CPU 不再持续占用且容器被清理。
4. 在沙箱中尝试访问公网和应用/数据库网段，必须失败；确认无网络、无宿主 socket/目录挂载、只读 rootfs、非 root、capability 为空及 `no-new-privileges`。
5. 触发内存、PID、tmpfs 与输出大小限制，确认被终止/拒绝且不会影响相邻任务；并发超限应快速返回 429。
6. 强制重启 Runner/daemon，确认 TTL reaper 只移除标签 `v5ai.workflow-python-runner.managed=true` 的过期容器，不碰其他容器。

这些现场检查完成前，不得把普通 Docker Desktop、`runc` 或本地单元测试描述为生产隔离通过。

## Python 节点 Java ↔ Runner 联调脚本

在 Workflow 编辑器中新建最小流程 `START → PYTHON → END`，给 START 输入 `{"value":21}`。打开 Python 节点，把下面脚本粘贴到代码框（当前编辑器字段名为 `code`，Java 端也兼容旧 `code` 字段）；`outputVar` 可填写 `runnerSmokeTest`：

```python
payload = inputs.get("inputs", {})
value = payload.get("value", 21)

if isinstance(value, bool) or not isinstance(value, (int, float)):
    raise ValueError("value must be a number")

result = {
    "message": "Python Runner 联调成功",
    "inputValue": value,
    "doubledValue": value * 2,
}
```

运行调试后，Python 节点输出应为：

```json
{
  "message": "Python Runner 联调成功",
  "inputValue": 21,
  "doubledValue": 42
}
```

这条链路会由 Java `PythonNodeExecutor` 先调用带 Bearer Token 的 `GET /health`，再向 `POST /execute` 发送 `runId`、`nodeId`、脚本、工作流上下文快照和 `timeoutMs`；Runner 返回的 `outputs` 对象会成为该节点输出。`outputVar=runnerSmokeTest` 时，同一对象也会写入后续节点上下文，可用 `runnerSmokeTest.doubledValue` 引用。

更多可复制脚本见 [Python 节点脚本案例](../workflow/python-node-examples.md)。如失败，先检查 Java 错误里的 HTTP 状态与 Runner 错误码：401 核对 Token，503 检查 readiness/daemon/gVisor，422 检查脚本和 `result`，504 检查执行时限。不要把 Token 放进脚本或工作流输入。
