# v5ai-nb 设计文档

## 1. 项目概述

`v5ai-nb` 是一个基于 Spring Boot 4、JDK 21 与 AgentScope Java 构建的中心化企业级 AI 应用平台。项目参考 `snail-ai` 的业务能力，但作为全新项目独立开发，不修改、不依赖 `snail-ai` 的源码、模块、数据库和通信协议。

项目采用类似 Dify 的平台架构：平台统一管理模型、Agent、知识库、MCP Server、Skill、会话和运行记录；运行时根据已发布的 Agent 配置动态构建 AgentScope Agent，并通过 HTTP/SSE 对外提供能力。

固定命名约束：

- 项目名称：`v5ai-nb`
- Java 根包名：`xin.v5ai.nb`
- Maven 模块使用 `v5ai-*` 前缀，例如 `v5ai-common`、`v5ai-runtime`、`v5ai-rag`
- 数据库表使用 `v5ai_` 前缀，例如 `v5ai_application`、`v5ai_knowledge_base`
- 不使用 Server-Agent/gRPC 架构
- 不复用 `snail-ai` 的数据库表和 API 契约
- JDK 使用 21
- ORM 使用 MyBatis-Plus
- 认证使用 Sa-Token，并采用 JWT 会话模式

## 2. 目标与非目标

### 2.1 目标

第一阶段完成一个可运行的中心化 AI 应用平台，打通以下闭环：

```text
配置模型 → 创建 Agent → 绑定知识库/MCP/Skill → 发布 Agent
→ 通过 API 发起对话 → AgentScope 执行 → SSE 输出事件
→ 保存会话、消息和运行记录
```

平台需要具备以下能力：

- 多模型 Provider 与模型配置管理；
- 基于 AgentScope Java 的 ReAct/Tool Calling Agent；
- Agent 草稿、发布版本和运行时配置隔离；
- 知识库、文档解析、切片、Embedding、向量检索和引用返回；
- MCP Server 注册、连接测试、Tool 发现和 Agent 绑定；
- Skill 包上传、解析、版本发布、回滚和 Workspace 注入；
- 会话、消息、AgentState、运行事件和用量记录；
- 管理 API、运行 API 和 SSE 流式事件；
- 异步执行文档处理和其他长任务。

### 2.2 非目标

第一阶段不实现：

- Server-Agent、gRPC、客户端节点注册和节点路由；
- 可视化 Workflow 编辑器；
- 完整插件市场；
- 多集群调度和独立 Agent Runtime 集群；
- 所有模型、向量库和对象存储 Provider；
- 对 `snail-ai` 的兼容迁移。

## 3. 总体架构

采用模块化单体加异步 Worker 的中心化部署方式。API 服务负责管理、会话和实时流式请求；Worker 负责文档解析、Embedding、向量入库、Skill 处理和其他异步任务；二者共享数据库、Redis 和对象存储。

```text
管理后台 / Chat UI / OpenAPI Client
                |
             HTTP/SSE
                v
        +------------------+
        | v5ai API         |
        | Admin + Runtime  |
        +--------+---------+
                 |
        +--------v---------+
        | AgentScope       |
        | Runtime          |
        +--+-----+------+--+
           |     |      |
         Model  RAG  Tool/MCP/Skill
           |     |      |
        PostgreSQL + pgvector + Redis + MinIO
                 ^
                 |
        +--------+---------+
        | v5ai Worker      |
        | Knowledge Tasks  |
        +------------------+
```

运行时不由 Controller 直接组装。所有运行请求都进入 `v5ai-runtime`，由它读取已发布 Agent、解析依赖并构造 AgentScope Agent。

## 4. Maven 模块与职责

```text
v5ai-nb/
├── v5ai-common
├── v5ai-domain
├── v5ai-infrastructure
├── v5ai-model
├── v5ai-runtime
├── v5ai-rag
├── v5ai-mcp
├── v5ai-skill
├── v5ai-agent
├── v5ai-api
├── v5ai-worker
├── v5ai-starter
└── v5ai-ui
```

所有 Java 包以 `xin.v5ai.nb` 开头，模块内按领域继续划分，例如 `xin.v5ai.nb.runtime`、`xin.v5ai.nb.rag`。

| 模块 | 职责 |
|---|---|
| `v5ai-common` | 通用响应、分页、异常、ID、JSON、时间、枚举和基础工具 |
| `v5ai-domain` | 领域模型、领域服务接口、Repository 接口和业务规则 |
| `v5ai-infrastructure` | MyBatis-Plus、Redis、MinIO、加密、Flyway 和外部客户端 |
| `v5ai-model` | Provider、Chat/Embedding/Rerank 模型配置、解析和连通性测试 |
| `v5ai-runtime` | AgentScope Agent 构建、Context、Tool、Middleware、事件和状态管理 |
| `v5ai-rag` | 知识库、文档、解析、切片、向量化、检索和引用 |
| `v5ai-mcp` | MCP Server、凭据、连接、Tool 发现、缓存和运行时适配 |
| `v5ai-skill` | Skill 包、`SKILL.md`、版本、发布和 Workspace 内容 |
| `v5ai-agent` | Agent 配置、资源绑定、草稿、发布版本和校验 |
| `v5ai-api` | 管理 API、运行 API、会话 API、SSE 和认证入口 |
| `v5ai-worker` | 知识库任务、Skill 任务、连接测试和可恢复异步任务 |
| `v5ai-starter` | Spring Boot 启动类、自动配置、配置文件和部署入口 |
| `v5ai-ui` | 管理后台、Chat UI 和 OpenAPI Client |

依赖方向必须保持单向：`api/application/runtime/rag/mcp/skill/model` 依赖 `domain`，基础设施实现 `domain` 的接口；业务模块不反向依赖 API。

## 5. 核心领域模型

### 5.1 Agent

Agent 是平台对外发布的 AI 应用单元，包含系统提示词、模型、知识库、MCP、Skill、工具和记忆策略。

```text
Agent
├── id / agentKey / name / description
├── type: CHAT | COMPLETION
├── status: DRAFT | PUBLISHED | DISABLED
├── draftVersion
├── publishedVersion
└── resource bindings
```

运行时只允许读取发布版本。发布前执行配置完整性检查：模型可用、绑定资源存在、MCP 配置合法、Skill 版本已发布、知识库状态可用。

### 5.2 Model

模型采用 Provider 与 Model 两层结构。Provider 保存供应商级信息，Model 保存具体模型和能力配置。

第一阶段优先支持：

1. OpenAI Compatible；
2. DashScope；
3. Ollama。

Provider 适配器通过统一接口提供凭据校验和运行时模型创建，API Key、Headers 和其他凭据使用应用级加密密钥加密保存。

### 5.3 Knowledge Base

知识库包含文档和文档切片。文档处理是异步任务，状态包括 `PENDING`、`PROCESSING`、`COMPLETED`、`FAILED`。

第一阶段支持 TXT、Markdown、PDF、DOCX 和 URL；向量存储使用 PostgreSQL + pgvector。检索结果必须保留文档、页码、文件名和切片编号等引用元数据。

### 5.4 MCP Server

MCP Server 保存传输类型、Endpoint/Command、参数、Headers、环境变量和超时配置。第一阶段支持 Streamable HTTP、SSE 和 Stdio。

MCP Tool 元数据可缓存，但运行时必须根据 Agent 已发布配置建立隔离的 Tool 集合。Stdio 命令执行必须受白名单、工作目录和超时限制。

### 5.5 Skill

Skill 是版本化能力包，最小结构如下：

```text
skill-package/
├── SKILL.md
├── metadata.yaml
├── prompts/
└── resources/
```

只有已发布版本可以绑定 Agent。运行时通过 Workspace 和 Context 注入 Skill 内容，禁止直接加载用户上传但未校验的文件。

## 6. AgentScope Runtime 设计

平台定义稳定的运行时门面，隔离 AgentScope API：

```java
public interface AgentRuntime {

    Flux<RuntimeEvent> stream(AgentRunRequest request);

    Mono<AgentRunResult> call(AgentRunRequest request);

    Mono<Void> resume(AgentResumeRequest request);
}
```

运行时内部组件：

```text
AgentScopeRuntime
├── PublishedApplicationResolver
├── ModelResolver
├── RetrievalContextBuilder
├── McpToolResolver
├── SkillWorkspaceResolver
├── AgentFactory
├── RuntimeMiddlewareFactory
├── PermissionManager
├── AgentStateManager
└── RuntimeEventPublisher
```

每次运行的步骤：

1. 根据 `agentKey` 加载 Agent 发布版本；
2. 根据 `userId`、`conversationId` 加载上下文；
3. 解析 Chat Model；
4. 执行知识检索并构造引用上下文；
5. 加载 Skill Workspace；
6. 创建 MCP Client 并注册可用 Tools；
7. 创建 AgentScope `HarnessAgent` 或基础 Agent；
8. 订阅 AgentScope 类型化事件；
9. 转换为平台 `RuntimeRunEventDTO`；
10. 持久化消息、运行状态和事件摘要。

AgentScope 的事件、Middleware、Tool、Permission、Workspace 和 AgentState 是底层运行能力；平台负责配置解析、凭据治理、审计和 API 协议转换。

## 7. RAG 流程

```text
上传文档
 → 创建 v5ai_knowledge_task
 → 解析和清洗
 → 切片
 → Embedding
 → 写入 pgvector
 → 更新文档状态
```

运行时检索：

```text
用户问题
 → Query 预处理
 → 向量检索
 → 可选关键词检索
 → 可选 Rerank
 → 生成引用
 → 注入 AgentScope Context
```

RAG 作为 Runtime 的上下文构建阶段，而不是强制暴露为普通 Tool，以便统一控制检索时机、上下文长度、引用格式和失败降级策略。

## 8. API 设计

管理 API：

```text
/api/admin/models
/api/admin/agents
/api/admin/knowledge-bases
/api/admin/documents
/api/admin/mcp-servers
/api/admin/skills
/api/admin/resources
/api/admin/runs
```

运行 API：

```text
POST /api/v1/agents/{agentKey}/chat
POST /api/v1/agents/{agentKey}/chat/stream
GET  /api/v1/conversations/{conversationId}
POST /api/v1/conversations/{conversationId}/resume
```

运行 API 默认使用 SSE。平台事件统一包含 `runId`、`conversationId`、事件类型、时间戳和可选 payload。事件类型至少包括：

- `run_started`
- `model_call`
- `text_delta`
- `tool_call`
- `tool_result`
- `permission_required`
- `message_completed`
- `run_failed`
- `run_completed`

AgentScope 事件映射为平台事件，不向客户端暴露 AgentScope 内部类名。

## 9. 数据库设计

表名统一使用 `v5ai_` 前缀：

```text
v5ai_user
v5ai_api_key

v5ai_model_provider
v5ai_model
v5ai_model_usage

v5ai_application
v5ai_application_version
v5ai_application_model
v5ai_application_knowledge
v5ai_application_mcp
v5ai_application_skill

v5ai_knowledge_base
v5ai_knowledge_document
v5ai_knowledge_chunk
v5ai_knowledge_task

v5ai_mcp_server
v5ai_mcp_credential
v5ai_mcp_tool

v5ai_skill
v5ai_skill_version
v5ai_skill_file

v5ai_conversation
v5ai_message
v5ai_agent_state
v5ai_run
v5ai_run_event
```

所有业务表包含创建时间、更新时间和必要的租户字段；删除优先使用逻辑删除。敏感字段不以明文写入数据库或日志。

## 10. 安全与可靠性

- 管理 API 使用 Sa-Token + JWT/RBAC；运行 API 使用 Agent API Key。
- 模型凭据、MCP Headers、环境变量和第三方 Token 加密存储。
- 所有 MCP Tool 调用记录 `runId`、工具名、参数摘要、结果状态和耗时。
- Tool 权限支持允许、人工审批、拒绝三态决策。
- Stdio MCP 禁止任意命令和任意工作目录。
- 文档和 Skill 上传进行文件类型、大小、路径穿越和压缩包目录校验。
- 异步任务使用状态机和幂等任务键，失败支持重试，超过重试次数进入 `FAILED`。
- AgentState 与会话消息分离存储，支持 Redis 或数据库恢复。
- 生产部署首先支持 API 多实例共享 PostgreSQL/Redis，暂不引入 Agent 节点路由。

## 11. 测试策略

### 单元测试

- Agent 发布校验；
- 模型 Provider 配置解析；
- 文档切片；
- Skill 包结构校验；
- MCP 配置校验；
- AgentScope 事件到 RuntimeEvent 的映射；
- 权限三态决策。

### 集成测试

- PostgreSQL + pgvector 知识库流水线；
- Redis 任务状态和 AgentState；
- MinIO 文件上传；
- OpenAI Compatible Mock Model；
- MCP Streamable HTTP Mock Server；
- SSE 对话完整链路。

### 验收测试

完成以下端到端场景：

1. 创建模型并通过连通性测试；
2. 创建 Agent、绑定模型并发布；
3. 通过 SSE 完成一次基础对话；
4. 上传文档并完成知识库索引；
5. Agent 使用知识库回答并返回引用；
6. Agent 调用 MCP Tool 并输出工具事件；
7. Agent 加载 Skill 并完成对应任务；
8. 失败任务可查询并重试；
9. 会话恢复后上下文保持一致。

## 12. 技术基线

| 类别 | 选择 |
|---|---|
| JDK | 21 |
| Web | Spring Boot WebFlux/SSE |
| ORM | MyBatis-Plus |
| 认证 | Sa-Token + JWT |
| 数据库 | PostgreSQL |
| 向量存储 | pgvector |
| 缓存/任务 | Redis |
| 对象存储 | MinIO |
| 迁移 | Flyway |
| Agent Runtime | AgentScope Java 2.0 |

## 13. 分阶段交付

### Phase 0：项目骨架

Maven 多模块、Spring Boot、PostgreSQL、Redis、MinIO、Flyway、认证、统一错误模型和 AgentScope 最小调用。

### Phase 1：模型与 Agent

Provider/Model 管理、Agent、发布版本、AgentScope Runtime、基础对话、SSE、会话和消息持久化。

### Phase 2：RAG

知识库、文档上传、解析、切片、Embedding、pgvector、检索、引用和异步任务。

### Phase 3：MCP

MCP Server、连接测试、Tool 发现、Agent 绑定、动态 Tool 注册、权限和审计。

### Phase 4：Skill

Skill 上传、`SKILL.md` 解析、版本发布、Workspace 注入、绑定、回滚和禁用。

### Phase 5：平台增强

多租户、RBAC、API Key、限流、配额、用量、审计、AgentState 持久化和可观测性。

### Phase 6：Workflow

在 Agent 基础闭环稳定后，再实现节点、变量、条件、工作流运行记录和可视化编排。

## 14. 设计决策总结

1. 用 Agent 作为发布和运行边界，而不是暴露 Agent Client。
2. 用 AgentScope Runtime 隔离底层 Agent API 与平台业务。
3. 用发布版本保证运行配置稳定。
4. 用异步 Worker 处理知识库和长任务。
5. 用 RAG Context Builder 控制知识注入，而不是把所有能力简单工具化。
6. 用 MCP Registry 管理外部工具，用权限系统控制执行。
7. 用 Skill 版本和 Workspace 实现能力沉淀。
8. 第一阶段采用模块化单体，保留未来拆分 API、Worker 和 Runtime 的边界。
9. 先完成 Chat Agent 闭环，再实现 Workflow。
