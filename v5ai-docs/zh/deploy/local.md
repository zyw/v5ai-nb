# 本地开发部署

本页给出最短本地开发流程。数据库、Redis、加密配置和可选服务的完整说明见仓库 `docs/deploy/dev.md`。

## 1 · 准备依赖

安装 JDK 21、Maven 3.9+、Node.js 22+、PostgreSQL 14+、pgvector 和 Redis 6+。如果使用 MySQL 8.0.17+ 代替 PostgreSQL，请参照[配置与环境变量](/zh/reference/configuration)切换方言，并为 RAG 准备独立向量存储。

创建空业务库，并确认 PostgreSQL 中可用 `vector` 扩展。Flyway 会在首次启动时创建表和种子数据；不要手工修改已经执行过的迁移文件。

## 2 · 设置环境变量

从仓库根 `.env.example` 复制并填写自己的连接和密钥。直接用 shell 启动时，先导出变量：

```bash
cp .env.example .env
# 编辑 .env，填写数据库、Redis、JWT 和 32 字节凭据密钥
set -a
source .env
set +a
```

`application-dev.yml` 的默认数据库与 Redis 地址可能指向特定开发环境，因此本机启动应显式覆盖连接变量。`.env` 不应提交到仓库。

## 3 · 启动后端

```bash
mvn -pl v5ai-starter -am package -DskipTests
java -jar v5ai-starter/target/v5ai-starter-0.1.0-SNAPSHOT.jar
```

检查 `http://localhost:8080/api/health`，并在日志中确认 Flyway 迁移和 `Started V5aiApplication`。需要运行单元测试时，根 POM 默认跳过测试，需显式加 `-Dmaven.test.skip=false`。

## 4 · 启动两个前端

分别在两个终端运行：

```bash
cd v5ai-ui
npm install
npm run dev
```

```bash
cd v5ai-ui-chat
npm install
npm run dev
```

默认开发端口：管理端 `80`，对话门户 `5174`；两者通过 Vite 的 `/dev-api` 代理访问后端 `8080`。端口被占用时可调整对应前端的 `.env`。

## 5 · 验证一条完整链路

使用首次种子管理员登录管理端，创建 Provider 和 `CHAT` 模型并测试连接；创建 Agent、调试和发布；生成绑定该 Agent 的 API Key；最后在对话门户用 Key 登录并提问。问题定位见[常见问题](/zh/help/troubleshooting)。
