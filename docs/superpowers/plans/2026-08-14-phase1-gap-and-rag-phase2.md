# v5ai-nb Phase 1 缺口补齐 + RAG Phase 2 实施计划

> 依据 `docs/superpowers/specs/2026-08-12-v5ai-nb-design.md` 与 `docs/phase1-verification-update.md`。
> 目标：修复 Phase 1 已知缺口，并完成 RAG（知识库）Phase 2 闭环。

## 全局约束

- 项目名 `v5ai-nb`，包根 `xin.v5ai.nb`，模块 `v5ai-*`，表 `v5ai_*`。
- 不使用 Server-Agent/gRPC；不依赖 `snail-ai` 源码。
- JDK 21、Spring Boot 4.1.0、MyBatis-Plus、Sa-Token + JWT、PostgreSQL + pgvector、Flyway。
- API DTO 不暴露 AgentScope Java SDK 类型。
- 运行时只读已发布 Agent。
- 新增行为先写失败测试，再实现（沿用现有 Recording-Mapper 单测模式）。

## 关键发现（Phase 1 隐藏缺口）

`V5aiApplication` 位于 `xin.v5ai.nb.starter` 包，默认组件扫描只覆盖 `xin.v5ai.nb.starter.**`，
导致 `xin.v5ai.nb.api/.infrastructure/.model/.application/.runtime` 的全部 Bean（Controller、Service、
Repository、Configuration）从未被注册：应用能启动但没有任何 API。修复方式：
`@SpringBootApplication(scanBasePackages = "xin.v5ai.nb")`，并补齐扫描后缺失的 Bean
（`Predicate<Long>` 模型启用判定、`PasswordEncoder`）。

## Part A — Phase 1 缺口清单

1. **启动装配修复**：`scanBasePackages` + `ModelEnabledPredicate` + `PasswordEncoder` Bean；
   新增 starter 集成测试断言 Controller/Service Bean 存在（先红后绿）。
2. **Provider/Model CRUD 补全**：领域记录增加 `id`；`update/delete/findById` 端口与 MyBatis 实现；
   `ModelResponse`/`ModelProviderResponse` 暴露 `id`（前端 modelId 不再手填）；
   新增 `PUT/DELETE /api/admin/providers/{providerKey}`、`PUT/DELETE /api/admin/models/{id}`。
3. **模型连通性测试**：`ModelConnectionTester` 端口（v5ai-model）+ AgentScope 实现（infrastructure），
   `POST /api/admin/models/{id}/test`。
4. **Agent 补全**：`description` 字段、更新、禁用；`GET /api/admin/agents/{agentKey}/versions`；
   `ApplicationResponse` 暴露 `description`。
5. **事件模型扩展**：`RuntimeEventType` 增加 `MODEL_CALL/TOOL_CALL/TOOL_RESULT/PERMISSION_REQUIRED/
   MESSAGE_COMPLETED/RUN_FAILED/RETRIEVAL`；`RunRecordRepository.fail(runId, message)`；
   `PersistingAgentRuntime` 失败时发 `RUN_FAILED` 并落库、完成时发 `MESSAGE_COMPLETED`。
6. **流式执行器重构**：新增 `ModelStreamTextExecutor`（`Model.stream` 逐块文本增量、发 `MODEL_CALL`、
   支持 RAG 上下文注入）；保留 HarnessAgent 占位路径。
7. **会话历史与恢复**：`MessageRepository.findByConversationId`、`GET /api/v1/agents/{agentKey}/conversations/{id}`；
   `POST /api/v1/agents/{agentKey}/conversations/{id}/resume`（带历史上下文重跑并持久化）。
8. **AgentState 持久化**：`AgentStateService`（save/load）+ MyBatis 适配（`v5ai_agent_state`）+ 测试。
9. **API Key 存储升级**：`MyBatisApplicationApiKeyStore`（读 `v5ai_api_key` 表）替换开发期配置存储；
   `POST /api/admin/agents/{agentKey}/api-keys`（生成并 BCrypt 存哈希）、`GET/DELETE`。

## Part B — RAG Phase 2

1. **模块与迁移**：新增 `v5ai-rag`、`v5ai-worker` 模块；父 POM 注册；
   `V2__rag_schema.sql`：`CREATE EXTENSION vector`、`v5ai_knowledge_base`、
   `v5ai_knowledge_document`（含 BYTEA content）、`v5ai_knowledge_chunk`（`vector(1536)` 列 + ivfflat 索引）、
   `v5ai_knowledge_task`（状态机 + 重试计数）、`v5ai_application_knowledge`（agent_key + kb_id 绑定）。
2. **v5ai-rag 领域与端口**：`KnowledgeBase/Document/Chunk/Task` 记录与枚举；Repository 端口；
   `DocumentParser`、`DocumentChunker`、`EmbeddingClient`、`VectorStore` 端口；
   `KnowledgeManagementService`（建库、上传、任务提交/重试、绑定）；`RetrievalContextBuilder`
   （agentKey → 绑定知识库 → 向量/关键词检索 → 引用上下文）。
3. **v5ai-infrastructure 实现**：实体/Mapper/Gateway/MyBatis 仓库；
   TXT/MD、PDF（PDFBox 3.0.7）、DOCX（zip+XML 轻量解析）解析器；固定 800/100 切片器；
   `OpenAiCompatibleEmbeddingClient`（Java HttpClient 调 `/v1/embeddings`，未配置 EMBEDDING 模型时
   回退确定性 `HashEmbeddingClient`，维度 1536）；`PgVectorStore`（JdbcTemplate + `<=>` 余弦检索，
   无向量库时回退关键词 ILIKE）；`MyBatisApplicationKnowledgeBindingRepository`。
4. **v5ai-worker**：`KnowledgeIndexingService`（PENDING→PROCESSING→COMPLETED/FAILED 状态机、
   attempt/max_attempts 重试、幂等 re-index）；`KnowledgeTaskScheduler`（`@Scheduled` 扫描 PENDING/可重试 FAILED）。
5. **v5ai-api**：`KnowledgeBaseController`（CRUD）、`KnowledgeDocumentController`
   （multipart 上传、URL 导入、列表、重试、删除）、任务查询；`PUT/GET /api/admin/agents/{agentKey}/knowledge-bindings`。
6. **v5ai-runtime 集成**：`RagContextProvider` 端口（v5ai-runtime），impl 在 v5ai-rag；
   `AgentScopeRuntime` 可选注入，检索结果以 `RETRIEVAL` 事件输出、上下文注入
   `AgentRunRequest.ragContext` → `ModelStreamTextExecutor` 拼入提示词。
7. **前端**：`client.ts` 新增类型与函数；`ModelsView` 凭据按 Provider 表单化 + 连通性测试按钮；
   `ApplicationsView` 模型下拉选择 + 知识库绑定；新增 `KnowledgeBasesView`（建库、上传、状态、重试）；
   router 注册。
8. **验证与文档**：`mvn clean verify` 全绿、`npm run build` 通过；更新 README、`docs/api/phase2.md`、
   验证文档。

## 依赖方向

`api → runtime → rag → application/domain`；`infrastructure` 实现全部端口；
`worker → rag`（端口）+ infrastructure（实现）；`starter → api + worker`。
