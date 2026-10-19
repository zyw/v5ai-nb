# Docker Compose 部署

仓库提供按业务数据库方言分开的两份 Compose 文件：PostgreSQL + pgvector，或 MySQL 8.0。每份都包含数据库、Redis、后端、Python Runner 和 Nginx；Nginx 发布管理端 `/`、对话门户 `/chat/`，并将 `/prod-api` 转发给后端。

## 选择数据库

| Compose 文件 | 业务数据库 | RAG 向量存储 |
|---|---|---|
| `script/docker/docker-compose-postgresql.yml` | PostgreSQL + pgvector | 可用 pgvector 存储实例 |
| `script/docker/docker-compose-mysql.yml` | MySQL 8.0 | 另配 Milvus 或 Elasticsearch 存储实例 |

同一部署只运行其中一份。MySQL 基线用于新空库，不会重放 PostgreSQL 的历史迁移，也不自动迁移现有数据。

## 启动 PostgreSQL 方案

在仓库根目录：

```bash
cp .env.example .env
# 编辑 .env，替换数据库、Redis、JWT 和凭据加密密钥等值
cd script/docker
docker compose -f docker-compose-postgresql.yml --env-file ../../.env up -d --build
docker compose -f docker-compose-postgresql.yml ps
docker compose -f docker-compose-postgresql.yml logs -f v5ai
```

MySQL 方案改用 `docker-compose-mysql.yml`，同时设置 `MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD`。

## 验证和维护

健康接口为 `http://127.0.0.1:8080/api/health`。Nginx 的 HTTP 端口由 `NGINX_HTTP_PORT` 控制，默认 `80`。首次空库启动后可用种子账号登录，随后更换密码。

停止服务但保留卷：

```bash
docker compose -f docker-compose-postgresql.yml down
```

不要随意使用 `down -v`，它会移除数据库、Redis 和上传文件等命名卷。

::: warning Python Runner
Runner 默认在没有固定执行镜像或独立 Linux gVisor daemon 时保持未就绪，Python 节点失败关闭。要用于生产，先完成仓库 `docs/deploy/workflow-python-runner.md` 所列隔离验收。
:::

更多 Compose 变量、Nginx 路由和回滚步骤见仓库根 `README.md` 的“Docker Compose 部署”。
