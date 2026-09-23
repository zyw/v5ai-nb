# AGENTS.md

本文件帮助 Agent（人类或 AI）快速理解 `v5ai-nb` 项目：架构、模块、关键约定、构建方式与常见任务入口。详细设计见 `docs/superpowers/specs/2026-08-12-v5ai-nb-design.md`，API 契约见 `docs/api/phase*.md`。

## 1. 项目概览

`v5ai-nb` 是一个**中心化 AI 应用平台**（Dify 风格），基于 JDK 21 + Spring Boot 4.1.0（WebFlux）+ AgentScope Java 2.0 构建。平台统一管理模型、Agent、知识库（RAG）、MCP Server、Skill、会话与运行记录；运行时根据已发布的 Agent 配置动态构造 AgentScope Agent，通过 HTTP/SSE 对外提供对话能力。

**明确的架构决策**：
- 不使用 Server-Agent / gRPC 架构（中心化单体 + 异步 Worker）。
- 不依赖、不复用 `snail-ai` 的源码/数据库表/API 契约（本项目为全新独立实现）。
- 模块化单体（RuoYi-Vue-Plus 风格）：业务模块自包含 MyBatis-Plus 实体/BO/VO/Mapper/Service/Controller；`v5ai-common-*` 提供公共能力（`R`、`PageQuery`、`PageResult`、`QueryBuilder`、`BaseMapperPlus`、`MapstructUtils`、`CredentialCipher` 等）；`v5ai-infrastructure` 只保留跨模块的运行时/平台实现与 Flyway 迁移。

**当前阶段**：Phase 1（模型与 Agent 闭环）、Phase 2（RAG）、Phase 3（MCP）、Phase 4（Skill）、Phase 5（平台增强：RBAC/配额/限流/用量/审计/菜单）均已完成；**Workflow（工作流）为当前进行中的下一阶段**（模块与基础 API 已落地，可视化编辑器与运行时闭环待完善）。

## 2. 技术基线

| 组件 | 版本/说明 |
|---|---|
| JDK | 21（`maven.compiler.release=21`） |
| Spring Boot | 4.1.0（Servlet MVC 栈，非 WebFlux；响应式仅用于运行时事件流/SSE） |
| ORM | MyBatis-Plus 3.5.12（显式装配 `SqlSessionFactory` 以兼容 Spring Boot 4） |
| 数据库 | PostgreSQL 14+ + pgvector（`vector(1536)` 余弦检索），Flyway 迁移 |
| 认证 | Sa-Token 1.44.0 + JWT（`is-concurrent: false`，token 前缀 `Bearer`） |
| 加密 | BCrypt（API Key / 密码）、AES-GCM（模型凭据） |
| 运行时 | AgentScope Java 2.0（`agentscope-harness`、`extensions-model-openai/dashscope`） |
| PDF 解析 | PDFBox 3.0.7 |
| 前端 | Vue 3.5 + Vite 7 + TypeScript 5.9 + Naive UI 2.43 + Pinia + Vue Router 4 |
| 可选中间件 | Redis（Redisson）、MinIO（当前业务未强制使用） |

## 3. Maven 模块与依赖方向

根包名 `xin.v5ai.nb`，模块前缀 `v5ai-*`。依赖方向严格单向：

```
common ← model ← mcp
common ← agent ← skill
common ← rag ← worker
common + agent + mcp + skill ← runtime ← workflow
common + model + runtime + rag + mcp + skill + workflow + agent ← infrastructure（跨模块运行时/平台实现）
全部 ← api（管理/运行 API 与剩余 Web 装配）
api + infrastructure + worker ← starter（Spring Boot 启动入口）
```

| 模块 | 职责 |
|---|---|
| `v5ai-common` | 公共能力：统一响应（`R`）、异常（`V5aiException`/`ErrorCode`）、分页（`PageQuery`/`PageResult`）、MyBatis-Plus 扩展（`BaseMapperPlus`/`QueryBuilder`）、加解密（`CredentialCipher`）、API Key 校验端口（`AgentApiKeyVerifier`）、平台领域（用户/角色/菜单/配额/用量/审计/限流端口与模型） |
| `v5ai-model` | **自包含模块（新风格的样板）**：`domain` 实体（`V5aiModel`/`V5aiModelProvider`）、`domain/bo`、`domain/vo`、`mapper`（`BaseMapperPlus` + `ModelConfigRetrieve` 端口）、`service`/`service.impl`、`controller`（`/api/admin/models`、`/api/admin/providers`），以及运行时模型链（`runtime/AgentScopeModelFactory`、`runtime/AgentScopeModelConnectionTester`、`runtime/config/*`）。凭据 AES-GCM 加密，支持连通性测试 |
| `v5ai-agent` | **自包含模块**：`domain` 实体（`Agent`/`AgentVersion`）、`domain/bo`、`domain/vo`、`mapper`（`BaseMapperPlus` + `DatabaseAgentRepository` 实现 `AgentRepository` 端口）、`service`（`IAgentService`：草稿/发布/禁用/版本/分页）、`controller`（`/api/admin/agents`）；保留运行时端口与 record（`AgentRepository`/`PublishedAgentResolver`/`AgentConfigGenerator`/`Agent`），运行时链（`AgentRuntime`/`ChatController`）零改动；级联删除通过 `AgentDeletionPort` 由 infra 实现 |
| `v5ai-runtime` | Agent 运行时边界：`AgentRuntime`（stream/call）、事件模型（`RuntimeEvent`/`RuntimeEventType`）、会话/消息/run/run_event/AgentState 持久化端口、用量埋点、RAG 上下文端口（`RagContextProvider`）、MCP Tool 解析端口（`McpToolResolver`） |
| `v5ai-rag` | **自包含模块**：`domain` 实体（`KnowledgeBase`/`KnowledgeDocument`/`KnowledgeTask`/`AgentKnowledgeBinding`）、`bo`/`vo`、`mapper`（`BaseMapperPlus` + 端口实现 `MyBatis*Repository`）、`service`（`IKnowledgeBaseService`：知识库/文档/任务/绑定/URL 导入）、`controller`（`/api/admin/knowledge-bases`、`knowledge-bindings`）；保留运行时端口（`EmbeddingClient`/`VectorStore`/解析/切片/`RetrievalContextBuilder` 等），实现仍在 infra（PgVectorStore、OpenAiCompatibleEmbeddingClient、解析器） |
| `v5ai-mcp` | **自包含模块**：`domain` 实体（`McpServer`/`McpTool`/`McpToolCallAudit`/`AgentMcpBinding`，headers/env 密文列）、`domain/bo`、`domain/vo`、`mapper`（`BaseMapperPlus`）、`service`（`IMcpServerService`：连接测试、Tool 发现、权限三态、绑定、审计）、`controller`（`/api/admin/mcp-servers`、`/api/admin/mcp-tool-calls`、`agents/{agentKey}/mcp-bindings`）；保留运行时端口（`McpClientFactory`/`McpConnection`/`McpToolInfo`/`StdioCommandPolicy`/`McpToolPermissionPolicy`/`McpToolCallAuditRepository`），实现仍在 `v5ai-infrastructure`（AgentScope 适配） |
| `v5ai-skill` | **自包含模块**：`domain` 实体（`SkillDTO`/`SkillVersion`/`SkillFileDTO`/`AgentSkillBinding`）、`domain/bo`、`domain/vo`、`mapper`（`BaseMapperPlus`）、`service`（`ISkillService`：上传解析、版本发布/回滚、绑定）、`controller`（`/api/admin/skills`、`agents/{agentKey}/skill-bindings`）；保留纯逻辑与运行时端口（`SkillPackageParser`/`SkillFrontmatterParser`/`SkillWorkspaceResolver`/`ResolvedSkill`），`SkillWorkspaceResolver` 实现在 `v5ai-infrastructure` |
| `v5ai-workflow` | **自包含模块（进行中）**：`domain` 实体（`Workflow`/`WorkflowRun`/`WorkflowNodeRun`）、`bo`/`vo`、`mapper`（`BaseMapperPlus` + 运行时端口实现 `MyBatisWorkflowRepository`/`MyBatisWorkflowRunRepository`）、`service`（`IWorkflowService`：草稿/发布/禁用/运行查询）、`controller`（`/api/admin/workflows`）；保留纯逻辑（`WorkflowEngine`/`TemplateResolver`/`ConditionEvaluator`/`WorkflowDefinitionValidator`/`WorkflowJson`） |
| `v5ai-worker` | 文档索引 Worker（异步任务状态机 + `@Scheduled` + 重试） |
| `v5ai-platform` | **自包含平台模块**：auth（`platform.auth`：`AuthController`/`LoginServiceImpl`/`SaTokenWebConfiguration`/`PlmPermissionServiceImpl`，`platform.security`：`PasswordVerifier`/`BCryptPasswordVerifier`/`SaTokenConfiguration`）、apiKey（`platform.apiKey`：Agent API Key 管理/校验/过滤器 + `platform.domain.ApiKey`）、平台业务（`platform.domain`/`mapper`/`service.impl` 端口实现 + `platform.controller` 控制器：users/roles/menus/clients/logs/quota/usage/apiKey）、**db/migration（Flyway 迁移脚本，V23 已移除租户）**；common-core 的平台端口/record 保留为契约 |
| `v5ai-infrastructure` | 跨模块运行时/基础设施实现：MyBatis-Plus 装配（`mybatis/MyBatisConfiguration`）、Sa-Token/BCrypt/AES-GCM、pgvector、Redis/MinIO 配置、AgentScope 运行时适配（模型工厂、MCP 连接工厂、Skill Workspace 解析、RAG Embedding/向量/解析器）、Agent 级联删除（`AgentDeletionService`）、storage/reactor 配置 |
| `v5ai-api` | 运行 API（`/api/v1/...` SSE/Chat）、`/api/auth/login` 之外的 Web 装配、`StatsController`/`HealthController`（跨模块聚合与基础设施）、`ApiGlobalExceptionHandler`、`OptionResponse`（model/mcp/skill/agent/rag/workflow 的控制器已移入各自模块，auth/apiKey/平台控制器已移入 `v5ai-platform`） |
| `v5ai-starter` | 启动模块：`V5aiApplication`（`scanBasePackages="xin.v5ai.nb"` + `@MapperScan` + `@EnableScheduling`）、`application.yml` |
| `v5ai-ui` | Vue 3 管理前端（非 Maven 模块） |

## 4. 核心领域概念

- **Provider/Model**：模型供应商与模型配置；凭据 AES-GCM 加密存储；支持连通性测试。Embedding 模型未配置时回退到确定性本地 Hash 嵌入（1536 维）。
- **Agent**：平台核心实体。有草稿/发布/禁用状态，`systemPrompt` 随发布版本固化；`agentKey` 为对外运行标识；通过 API Key（BCrypt 存储）鉴权。
- **会话/消息/run/run_event/AgentState**：运行时持久化对象。对话历史与恢复基于这些表。
- **知识库（RAG）**：文档上传（TXT/MD/PDF/DOCX）与 URL 导入 → Worker 异步（解析→切片→Embedding→pgvector）→ 向量/关键词检索 → 运行时注入上下文并发出 `RETRIEVAL` 事件。
- **MCP**：Server 注册（Streamable HTTP / SSE / Stdio，headers/env 加密），连接测试 → Tool 发现与缓存 → Agent 绑定 → 运行时动态注册为 AgentScope Toolkit。**必须先"发现工具"再绑定发布**。Tool 权限三态 ALLOW/APPROVE/DENY，调用审计（runId/工具名/参数摘要/状态/耗时）。Stdio 命令受白名单限制（生产必配 `V5AI_MCP_STDIO_COMMAND_WHITELIST`）。
- **Skill**：zip 包上传（SKILL.md frontmatter 校验、路径穿越/大小/编码校验）→ DRAFT 版本 → 发布为当前版本（回滚改变当前版本）→ Agent 绑定 → 运行时注入 Workspace（`skills/<name>/` + 只读 Skill 仓库 `<available_skills>`）。
- **Workflow**（进行中）：节点/边定义、条件求值、模板解析、运行状态机（见 `v5ai-workflow`）。

## 5. 运行时事件流（RuntimeEventType）

一次 Agent 运行的完整事件序列（SSE 推送，顺序）：

```
RUN_STARTED → MODEL_CALL → (TEXT_DELTA | TOOL_CALL → TOOL_RESULT | RETRIEVAL)*
            → MESSAGE_COMPLETED → RUN_COMPLETED
失败路径：RUN_FAILED；需授权：PERMISSION_REQUIRED
```

实现链：`ChatController` → `AgentRuntime`（`AgentScopeRuntime`）→ `PersistingAgentRuntime`（持久化装饰器）→ `AgentScopeHarnessExecutor`/`ModelStreamTextExecutor`（模型执行器）。

## 6. API 概览

- **认证**：`POST /api/auth/login`（默认管理员 `admin/admin`，启动时自动种子）。管理 API 强制登录，写操作需 admin 角色。
- **管理 API**：`/api/admin/...`（providers、models、agents、knowledge-bases、mcp-servers、skills、workflows、users、roles、menus、clients、logs、quota、usage、stats）。
- **运行 API**：`/api/v1/agents/{agentKey}/chat/stream`（SSE）与 `/api/v1/agents/{agentKey}/chat`（一次性），用 Agent API Key 鉴权（Header `Authorization: Bearer <api-key>`），按应用配额/每分钟限流（超限 429）。
- **健康检查**：`/api/health`。

各阶段 API 细节见 `docs/api/phase1.md` ~ `phase5.md`。

## 7. 数据库与迁移

- 迁移脚本位于 `v5ai-platform/src/main/resources/db/migration/`，命名 `V<n>__<name>.sql`，Flyway 管理（表 `v5ai_flyway_schema_history`）。`spring.flyway.locations: classpath:db/migration` 扫描所有 classpath（v5ai-platform 为 starter 依赖）。
- 当前迁移：V1 Phase1、V2 RAG、V3 provider_type、V4 runtime 列改 varchar、V5 MCP、V6 Skill、V7 平台增强、V8 Agent 重命名、V9 角色、V10 菜单、V11 回填 admin 角色、V12 工作流。
- 所有表以 `v5ai_` 前缀。运行时表 UUID 列使用 VARCHAR（避免 `uuid = character varying` 报错）。
- 需要 pgvector 扩展（`CREATE EXTENSION IF NOT EXISTS vector` 在迁移中幂等执行）。
- 添加新表/改列时**必须新增一个递增的 V 版本迁移**，不要修改已应用的历史迁移。

## 8. 前端（v5ai-ui）

- Vue 3 + Vite 7 + TypeScript + Naive UI + Pinia + Vue Router。
- 结构：`src/api/client.ts`（API 类型与客户端）、`src/views/`（每页一个视图：Login、Dashboard、Agents、Models、KnowledgeBases、McpServers、Skills、Workflows、WorkflowEditor、UserManagement、RoleManagement、MenuManagement、ClientManagement、LogManagement、Observability、ChatDebug、Settings）、`src/router/index.ts`、`src/stores/`（session、theme）。
- 构建：`cd v5ai-ui && npm install && npm run build`（产物在 `dist/`）。
- **Naive UI 表格横向滚动约定**：`n-data-table` 是否出现横向滚动条取决于「列是否带 `ellipsis`」。任一列配置 `ellipsis` 时，Naive UI 会把表格切为 `table-layout: fixed`（源码 `DataTable.mjs` 的 `mergedTableLayoutRef`：`virtualScroll || flexHeight || maxHeight !== undefined || hasEllipsis` → `fixed`）；fixed 布局下**只有显式绑定 `:scroll-x="列宽总和"` 才会出现横向滚动条**，否则列宽被压缩而不滚动。反之，无 `ellipsis` 的表格保持 `table-layout: auto`，内容超宽时会自动横向滚动、无需 `scroll-x`（例：`StoreInstancesView.vue`）。因此约定：**列一旦用 `ellipsis`，就必须同时给 `:scroll-x`**，列宽总和用 `columns.reduce((s, c) => s + (typeof c.width === 'number' ? c.width : 240), 0)` 计算，参考 `WorkflowsView.vue` / `SkillsView.vue` / `ModelsView.vue`。

## 9. 构建与本地运行

```bash
# 后端（用本地仓库避免下载污染）
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dsurefire.failIfNoSpecifiedTests=false

# 前端
cd v5ai-ui && npm install && npm run build
```

前置条件：PostgreSQL + pgvector（库 `v5ai_nb`），环境变量参考 `.env.example`（`V5AI_DATASOURCE_URL`、`V5AI_DATASOURCE_USERNAME`、`V5AI_DATASOURCE_PASSWORD`、`V5AI_JWT_SECRET`、`V5AI_CREDENTIAL_CIPHER_KEY`，可选 Redis/MinIO/上传大小/超时/MCP 白名单）。开发配置模板：`v5ai-starter/src/main/resources/application-dev.yml.template`。

端到端验证步骤（登录 → 配模型 → 建 Agent → 生成 API Key → 建知识库/绑文档 → 建 MCP/发现工具/绑定 → 传 Skill/发布/绑定 → 流式对话）见 README「本地验证」。

## 10. 测试约定

- 61 个测试类，JUnit 5 + Mockito（各模块 `src/test/resources/mockito-extensions/` 提供 mockito 扩展）+ Reactor `StepVerifier`。
- 单测策略：领域模块与基础设施组件以纯单测为主；`v5ai-starter` 含集成测试（`src/test/resources/db/testmigration`、`db/idtest` 提供测试迁移/ID 方案）。
- 构建命令中 `-Dsurefire.failIfNoSpecifiedTests=false` 允许模块无测试时通过。
- 运行测试：`mvn test`（在根或指定模块执行）。

## 11. 代码风格与约定

- 包结构：`xin.v5ai.nb.<module>`，业务模块内部按 `domain`（实体）/`domain.bo`/`domain.vo`/`mapper`/`service`/`service.impl`/`controller` 组织（参考 `v5ai-model`，代码生成器在 `v5ai-modules/v5ai-code-generator`）；`v5ai-platform` 按 `auth`/`apiKey`/`security`/`domain`/`mapper`/`service.impl`/`controller` 组织；基础设施按 `infrastructure/<能力>` 组织（mybatis、redis、storage、runtime、agent、mcp、rag、skill、reactor）。
- 业务模块的 CRUD 采用 MyBatis-Plus 风格：实体继承 `common.mybatis.core.domain.BaseEntity`（`@TableName`/`@TableId`），BO 用 `@AutoMapper(target = Entity.class, reverseConvertGenerate = false)`，VO 用 `@AutoMapper(target = Entity.class)`，Mapper 继承 `BaseMapperPlus<Entity, Vo>`，查询用 `QueryBuilder.lambda(...)` + `selectVoPage/selectVoList`，分页统一 `PageQuery`/`PageResult`，转换用 `MapstructUtils.convert`，控制器继承 `common.web.core.BaseController` 并返回 `R<T>`。
- 跨模块的运行时/平台实现仍放 `v5ai-infrastructure`，通过 Spring 依赖注入装配。
- API 层的请求/响应体使用不可变 record（见 git log `feat(api): 添加不可变请求和响应体记录类型`）。
- 响应统一包装 `R`（`common.core.domain.R`）；错误统一走 `V5aiException` + `ErrorCode`。
- 运行时相关代码保持响应式（`Flux`/`Mono`）；Web 层为 Servlet MVC（上传用 `MultipartFile`，SSE 用 `ServerSentEvent`）。
- 提交信息使用 conventional commits（feat/fix/refactor/chore 等，中文描述）。
- 数据库迁移只增不改；新增能力要配套：业务模块（entity/BO/VO/mapper/service/controller）或基础设施实现 + UI + 迁移脚本 + 测试 + docs/api 文档。

## 12. 文档索引

| 文档 | 内容 |
|---|---|
| `docs/superpowers/specs/2026-08-12-v5ai-nb-design.md` | 总体设计（架构、模块、命名约束） |
| `docs/superpowers/plans/*.md` | 各阶段实施计划 |
| `docs/api/phase1.md` ~ `phase5.md` | 各阶段 API 契约 |
| `docs/phase*-verification*.md` | 各阶段验证报告 |
| `docs/deploy/dev.md` | 开发环境部署（PostgreSQL/pgvector/Redis/MinIO 安装与配置） |

## 13. 工作流约定（重要）

**开发新功能/较大改动前，必须先输出实现方案供用户确认，确认后才开始编码。**

流程：
1. 理解需求（复述理解 + 列出约束与不确定点）；
2. 输出方案（涉及文件、改动点、数据库迁移、API 变更、风险）；
3. 等待用户确认；
4. 确认后才写代码。

- 小改动（bug 修复、字段调整、样式微调）可跳过方案直接实现；
- 需求模糊时优先提问澄清，而不是猜测实现；
- 可在对话中输入 `/plan <需求>` 触发项目级方案模板（`.pi/prompts/plan.md`）。

## 14. 常见任务入口（给 Agent 的提示）

- **加一个新管理实体**（如某种资源）：按 `v5ai-model` 样板，在业务模块内补 `domain` 实体 + `domain/bo` + `domain/vo` + `mapper`（`BaseMapperPlus`）+ `service`/`service.impl` + `controller`（`@RequestMapping("/api/admin/...")`）→ 新增 Flyway 迁移 → ui 加 view/路由 → 写测试与 docs/api。
- **改模型/Provider 管理**：入口在 `v5ai-model`（`V5aiModelMapper`/`V5aiModelProviderMapper`/`IV5aiModelService`/`V5aiModelController`）；运行时模型链（`AgentScopeModelFactory`/`AgentScopeModelConnectionTester`/`ModelConfigRetrieve`）也在 `v5ai-model/runtime`。
- **改运行时行为**：入口在 `v5ai-runtime`（`AgentRuntime` 接口与各 Executor），持久化在 `PersistingAgentRuntime`，事件模型在 `RuntimeEvent`。
- **MCP/Skill 运行时集成**：接口在 `v5ai-runtime`（`McpToolResolver`、`ResolvedSkillAgentSkillService`），实现与 AgentScope 适配在 `v5ai-infrastructure/runtime`、`infrastructure/mcp`、`infrastructure/skill`。
- **继续 Workflow 阶段**：先读 `v5ai-workflow` 模块与 `docs/api/phase5.md`/迁移 V12，当前只有领域与基础 API，可视化编辑器与完整执行闭环待完成。
- **注意历史遗留命名**：代码/文档中可能残留 `Application` 字样（V8 迁移前概念），新代码一律用 `Agent`。

## Agent skills

### Issue tracker

Issues live in the repo's OneDev Issues (`https://onedev.v5ai.xin/v5ai-nb`), read/written via the OneDev REST API with a personal access token. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical triage roles + five wayfinder labels, default vocabulary (`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`, plus `wayfinder:map` / `wayfinder:research` / `wayfinder:prototype` / `wayfinder:grilling` / `wayfinder:task`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
