# Phase 2 Verification（RAG 知识库）

验证时间：2026-08-14

## 命令

```bash
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dsurefire.failIfNoSpecifiedTests=false
cd v5ai-ui && npm run build
```

结果：后端 `clean verify` exit 0（112 个测试，0 失败/0 错误），前端 `npm run build` exit 0。

## 本轮完成范围（Phase 1 缺口 + RAG Phase 2）

### Phase 1 缺口补齐

- **启动装配修复**（关键）：`V5aiApplication` 改为 `scanBasePackages = "xin.v5ai.nb"`，
  修复了所有 Controller/Service/Repository Bean 从未被注册的问题；`@MapperScan(annotationClass=Mapper.class)`
  只注册 Mapper 接口；显式 `MyBatisConfiguration` 提供 `SqlSessionFactory`/`SqlSessionTemplate`
  （规避 Boot 4 与 MyBatis-Plus 3.5.12 自动装配条件求值顺序不兼容）；新增 `ModelEnabledPredicate` 与
  `PasswordEncoder` Bean；starter 集成测试断言各 Controller/Service Bean 存在。
- **Provider/Model CRUD 补全**：`PUT/DELETE /api/admin/providers/{providerKey}`、
  `PUT/DELETE /api/admin/models/{id}`、`POST /api/admin/models/{id}/test`（AgentScope 连通性测试）；
  `ModelResponse`/`ModelProviderResponse` 暴露 `id`，列表查询改为 JOIN provider_key。
- **Agent 补全**：`description`、`PUT /{agentKey}`、`DELETE /{agentKey}`（禁用）、
  `GET /{agentKey}/versions`；V1 schema 与实体对齐（`v5ai_application.model_id/description`、
  `v5ai_application_version` 改为 agent_key + version + snapshot_json）。
- **事件模型扩展**：`RuntimeEventType` 增至 9 种（新增 MODEL_CALL/TOOL_CALL/TOOL_RESULT/
  PERMISSION_REQUIRED/RETRIEVAL/MESSAGE_COMPLETED/RUN_FAILED）；执行器契约改为
  `Flux<AgentTextEvent>`（Text/ModelCall）；新增 `ModelStreamTextExecutor`
  （`Model.stream` 逐块流式、支持 RAG 上下文与历史注入）；`PersistingAgentRuntime`
  失败时发 `RUN_FAILED` 并落 `v5ai_run` FAILED 状态，完成时发 `MESSAGE_COMPLETED` 并保存 AgentState。
- **会话历史与恢复**：`MessageRepository.findByConversationId`（v5ai_message 增加 agent_key 列）、
  `GET /api/v1/agents/{agentKey}/chat/conversations/{id}`、`POST .../resume`（历史上下文重跑）。
- **AgentState 持久化**：`AgentStateService` + MyBatis 适配（`v5ai_agent_state` upsert/load）。
- **API Key 存储升级**：`MyBatisApplicationApiKeyStore`（读 `v5ai_api_key` 表）替换开发期配置存储，
  新增 `POST /api/admin/agents/{agentKey}/api-keys`（生成并 BCrypt 存储）、`GET/DELETE`。

### RAG Phase 2

- 新模块 `v5ai-rag`（领域模型 + 端口 + 服务 + 检索上下文构建）、`v5ai-worker`
  （`KnowledgeIndexingService` 状态机 + `KnowledgeTaskScheduler` 定时扫描重试）。
- `V2__rag_schema.sql`：`CREATE EXTENSION vector`、`v5ai_knowledge_base`、
  `v5ai_knowledge_document`（BYTEA content）、`v5ai_knowledge_chunk`（`vector(1536)` + ivfflat 索引）、
  `v5ai_knowledge_task`（attempt/max_attempts）、`v5ai_application_knowledge`（agent_key 绑定）。
- 文档解析：TXT/Markdown/URL（UTF-8）、PDF（PDFBox 3）、DOCX（zip+XML 轻量解析）；固定 800/100 切片。
- Embedding：优先第一个启用的 EMBEDDING 模型（OpenAI-compatible `/v1/embeddings`），
  未配置时直接报错，要求先配置可用的 EMBEDDING 模型。
- 向量存储：`PgVectorStore`（JdbcTemplate + `<=>` 余弦检索），空结果回退 `ILIKE` 关键词检索；
  检索保留 `documentId/chunkIndex/title` 引用元数据并格式化为引用上下文。
- 运行时集成：`DBRagContextProvider` 注入 `AgentScopeRuntime`，检索命中时发 `RETRIEVAL` 事件，
  上下文经 `AgentRunRequest.ragContext` 注入 `ModelStreamTextExecutor` 的 System Prompt。
- Admin API：知识库 CRUD、文档 multipart 上传/URL 导入/删除/重试、任务查询、Agent 绑定。
- 前端：`KnowledgeBasesView`（建库、上传、状态/任务表、重试）、ModelsView 凭据表单化与连通性测试、
  ApplicationsView 模型下拉 + 知识库绑定 + 生成 Key；router 与菜单注册。

## 说明 / 限制

- `RETRIEVAL` 为平台扩展事件（设计文档 8 类之外新增）；`TOOL_CALL/TOOL_RESULT/PERMISSION_REQUIRED`
  已入事件模型，具体发射随 Phase 3 MCP 接入。
- pgvector 的 `CREATE EXTENSION vector` 需要数据库超级用户权限，或由 DBA 预先安装。
- 文档内容当前存储于数据库（BYTEA），对象存储（MinIO）归档留待后续阶段。
- MCP、Skill、Workflow、多租户/RBAC、用量统计仍未包含。
