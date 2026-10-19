# 配置与环境变量

后端配置位于 `v5ai-starter/src/main/resources/application.yml` 及 profile 文件；仓库根 `.env.example` 列出了可通过环境变量覆盖的常用项。Spring Boot **不会自动读取** `.env`，直接运行 jar 时需要通过 shell、服务管理器或部署平台注入环境变量。

## 必要连接和密钥

| 环境变量 | 用途 |
|---|---|
| `V5AI_DATASOURCE_URL`、`V5AI_DATASOURCE_USERNAME`、`V5AI_DATASOURCE_PASSWORD` | 业务数据库 |
| `V5AI_REDIS_HOST`、`V5AI_REDIS_PORT`、`V5AI_REDIS_PASSWORD` | Redis 连接 |
| `V5AI_JWT_SECRET` | 管理端 JWT 签名 |
| `V5AI_CREDENTIAL_CIPHER_KEY` | 模型与工具凭据加密，必须为 32 字节 |

部署时应显式设置自己的密码和密钥。更换凭据加密密钥后，已存的模型 API Key 等密文无法再解密，需重新录入。

## 数据库与存储

默认 `V5AI_DB_DIALECT=postgresql`。选 MySQL 时设置 `V5AI_DB_DIALECT=mysql` 并同时切换 JDBC URL；Flyway 会加载相应方言的迁移目录。两套数据库用于分别新建部署，不提供现有数据的自动跨库迁移。

通用文件资源默认落本地目录：`V5AI_STORAGE_TYPE=LOCAL`、`V5AI_STORAGE_LOCAL_DIR=./v5ai-upload`。若改为 `MINIO`，再配置 endpoint、access key、secret key 与 bucket。向量检索使用另行管理的存储实例，与业务库方言分离。

## Agent 与检索

| 配置 | 作用 |
|---|---|
| `V5AI_TAVILY_API_KEY` | 联网搜索工具；留空时工具不能正常搜索 |
| `V5AI_AGENTSCOPE_WORKSPACE` | 运行时 Workspace 与 Skill 注入目录 |
| `v5ai.agentscope.history.*` | 同会话历史的条数和字符预算 |
| `v5ai.chat.conversation-summary.*` | 窗口外历史摘要 |
| `v5ai.chat.attachment.*` | 图片体积、数量和读取预算 |

管理端 Agent 开关决定是否装配这些能力；服务器配置提供底层服务或预算。关闭联网搜索能力时，即使存在 Tavily Key 也不会注册工具。

## Workflow 外部执行

HTTP 节点的 `V5AI_WORKFLOW_HTTP_ALLOWED_HOSTS` 默认为空，表示禁止访问外部主机。Python 节点依赖 `V5AI_WORKFLOW_PYTHON_RUNNER_URL` 和 `V5AI_WORKFLOW_PYTHON_RUNNER_TOKEN`；缺少配置时失败关闭。生产 Runner 还需独立 Linux 节点、gVisor 和运行镜像，见仓库 `docs/deploy/workflow-python-runner.md`。

完整配置项、默认值与安全说明以仓库 `v5ai-starter/src/main/resources/v5ai-nb-配置文件详情.yml` 为准。生产部署前应逐项检查该文件和 `.env.example`，不要依赖开发用默认凭据。
