# v5ai-nb

基于 JDK 21、Spring Boot 4.1.0、AgentScope Java、MyBatis-Plus、PostgreSQL + pgvector 和 Sa-Token + JWT 构建的中心化 AI 应用平台（Dify 风格，不使用 Server-Agent/gRPC 架构）。

当前已完成 **Phase 1（模型与 Agent 闭环）**、**Phase 2（RAG 知识库）** 与 **Phase 3（MCP）**：

- 认证（Sa-Token + JWT）、Provider/Model 管理与凭据 AES-GCM 加密、模型连通性测试；
- Application 草稿/更新/禁用/发布版本、API Key 管理（BCrypt 存储）；
- AgentScope 运行时边界：流式/阻塞对话、SSE 平台事件（RUN_STARTED/MODEL_CALL/TEXT_DELTA/TOOL_CALL/TOOL_RESULT/PERMISSION_REQUIRED/MESSAGE_COMPLETED/RUN_FAILED/RUN_COMPLETED/RETRIEVAL 等）、会话/消息/run/run_event/AgentState 持久化、会话历史与恢复；
- RAG：知识库、文档上传（TXT/MD/PDF/DOCX）与 URL 导入、异步 Worker（解析 → 切片 → Embedding → pgvector 写入）、向量/关键词检索、运行时引用上下文注入与 RETRIEVAL 事件；
- MCP：Server 注册（Streamable HTTP / SSE / Stdio，headers/env 加密存储）、连接测试、Tool 发现与缓存、Application 绑定、运行时动态 Tool 注册（AgentScope Toolkit）、Tool 权限三态（ALLOW/APPROVE/DENY）与调用审计（runId/工具名/参数摘要/状态/耗时）、Stdio 命令白名单；
- 管理前端：登录、Provider/Model（凭据表单化 + 连通性测试）、Application（模型下拉 + 知识库/MCP 绑定 + 生成 Key）、知识库管理、MCP Server 管理、Chat Debug。

## 技术基线

- JDK 21
- Spring Boot 4.1.0 / WebFlux
- MyBatis-Plus（显式装配 `SqlSessionFactory`，兼容 Spring Boot 4）
- Flyway
- Sa-Token + JWT
- BCrypt / AES-GCM
- PostgreSQL + pgvector（`vector(1536)` 余弦检索）
- AgentScope Java 2.0（`agentscope-harness`、`agentscope-extensions-model-openai/dashscope`）
- PDFBox 3.0.7（PDF 解析）

## 模块

- `v5ai-common`: 公共响应、异常、分页和 API Key 校验端口
- `v5ai-domain`: 预留领域共享模块
- `v5ai-model`: 模型配置与凭证加密、连通性测试端口
- `v5ai-application`: Application 草稿/更新/禁用/发布/版本解析核心
- `v5ai-runtime`: Agent 运行时边界、事件模型、会话/消息/run/AgentState 持久化装饰器、RAG 上下文端口
- `v5ai-rag`: 知识库/文档/任务领域模型、解析/切片/Embedding/向量存储端口、检索上下文构建
- `v5ai-mcp`: MCP Server/工具/审计领域模型、管理服务（连接测试、Tool 发现、Application 绑定、权限）、连接与 Stdio 策略端口
- `v5ai-worker`: 文档索引 Worker（异步任务状态机 + `@Scheduled` 调度 + 重试）
- `v5ai-infrastructure`: MyBatis-Plus、Sa-Token、BCrypt、AES-GCM、pgvector、MinIO/Redisson、AgentScope MCP 传输实现、各端口实现
- `v5ai-api`: 管理 API、运行 API（SSE）、API Key 过滤器、知识库/文档/任务/绑定/MCP 管理 API
- `v5ai-starter`: Spring Boot 启动模块（`scanBasePackages="xin.v5ai.nb"` + `@MapperScan` + `@EnableScheduling`）
- `v5ai-ui`: Vue 3 + Vite + TypeScript + Naive UI 管理前端

## 本地验证

```bash
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dsurefire.failIfNoSpecifiedTests=false
cd v5ai-ui
npm install
npm run build
```

启动前需要 PostgreSQL（含 pgvector 扩展）与配置：

```bash
V5AI_DATASOURCE_URL=jdbc:postgresql://localhost:5432/v5ai_nb
V5AI_DATASOURCE_USERNAME=v5ai
V5AI_DATASOURCE_PASSWORD=change-me
V5AI_JWT_SECRET=change-this-development-secret
V5AI_CREDENTIAL_CIPHER_KEY=replace-with-32-byte-secret-value
```

启动后：

1. `POST /api/auth/login`（默认用户由 `v5ai_user` 表提供）获取管理员 Token；
2. 配置 Provider 与 Model（`POST /api/admin/providers`、`POST /api/admin/models`，可在 UI 上连通性测试）；
3. 创建并发布 Application，`POST /api/admin/applications/{appKey}/api-keys` 生成运行 API Key；
4. 创建知识库、上传文档（`POST /api/admin/knowledge-bases/{kbId}/documents`，multipart），Worker 异步索引；
5. 将知识库绑定到 Application（`POST /api/admin/applications/{appKey}/knowledge-bindings`）；
6. 创建 MCP Server（`POST /api/admin/mcp-servers`）→ 测试连接 → 发现工具（`POST /api/admin/mcp-servers/{id}/discover-tools`）→ 绑定到 Application（`POST /api/admin/applications/{appKey}/mcp-bindings`）；
7. 通过 `POST /api/v1/apps/{appKey}/chat/stream`（Application API Key）发起流式对话：带 RAG 上下文，且绑定的 MCP 工具会被动态注册，调用记录可查 `GET /api/admin/mcp-tool-calls?runId=<run-id>`。

说明：

- 未配置 EMBEDDING 模型时，向量化回退到确定性本地 Hash 嵌入（维度 1536），便于本地端到端验证；
- 数据库迁移：`v5ai-infrastructure/src/main/resources/db/migration/`（V1 Phase 1、V2 RAG、V5 MCP，需 pgvector）；
- MCP 运行时使用已发现的 Tool 元数据：先"发现工具"再"绑定 + 发布"，运行时才会注册工具；
- Stdio MCP 命令受白名单限制（`V5AI_MCP_STDIO_COMMAND_WHITELIST`，生产环境必须配置）；
- Skill、Workflow、多租户/RBAC/用量统计等仍属后续阶段。

更多说明见：

- `docs/deploy/dev.md`（开发环境部署文档）
- `docs/api/phase1.md`
- `docs/api/phase2.md`
- `docs/api/phase3.md`
- `docs/phase1-verification-update.md`
- `docs/phase2-verification.md`
- `docs/phase3-verification.md`
