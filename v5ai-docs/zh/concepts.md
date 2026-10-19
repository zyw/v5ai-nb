# 平台概念与架构

v5ai-nb 是中心化的模块化单体。业务模块各自承载实体、服务和控制器；运行 API 在进程内组合模型、知识与工具，通过 HTTP/SSE 向调用方输出结果。

## 请求怎样穿过平台

| 入口 | 经过 | 得到 |
|---|---|---|
| 管理端 `v5ai-ui` | 管理 API → 各业务模块 | 模型、Agent、知识库、工具与发布配置 |
| 对话门户 `v5ai-ui-chat` / 外部应用 | 运行 API → AgentScope 运行时 → 模型、RAG、MCP、Skill | 流式回答、引用和工具事件 |
| 运行过程 | 持久化装饰器 → 业务库 | 会话、消息、Run、事件与用量记录 |

后端使用 JDK 21、Spring Boot 4.1.0 的 Servlet MVC 栈；SSE 运行事件使用响应式流。业务库可用 PostgreSQL 或 MySQL，RAG 向量数据由独立存储实例承载。Redis 用于登录会话、刷新令牌和验证码等平台状态。

## 核心对象

| 对象 | 用途 | 关键行为 |
|---|---|---|
| Provider / Model | 注册供应商、协议适配器和对话或嵌入模型 | 凭据加密；支持连通性测试与引用保护 |
| Agent | 组合模型、提示词和能力开关 | 草稿可调试；发布版本供线上读取 |
| Knowledge Base | 管理文档、切片、索引与检索 | 文档异步解析和索引，命中可作为回答引用 |
| MCP Server | 注册外部工具 | 先发现工具，再设置权限和绑定 Agent |
| Skill | 可版本化的文件包 | 发布后注入运行时 Workspace |
| Conversation / Run | 保存会话与每次运行 | 消息、事件、用量和取消状态可追溯 |
| Workflow | 编排节点和边 | 管理、编辑与基础执行已落地；完整闭环仍在完善 |

## 草稿调试与线上发布

管理端调试读取 Agent 当前编辑行，草稿也能运行；线上 `/api/v1` 则读取最近发布的配置快照。编辑模型、提示词、次要模型或展示开关后，需重新发布才会影响线上。知识库、MCP 和 Skill 的绑定列表按 `agentKey` 实时解析。

## 运行时事件

流式对话会输出 `RUN_STARTED`、`MODEL_CALL`、`TEXT_DELTA`、`TOOL_CALL`、`TOOL_RESULT`、`RETRIEVAL`、`MESSAGE_COMPLETED`、`RUN_COMPLETED` 等事件。强制 RAG 检索发生在模型调用前；智能 RAG 则由模型通过 `rag_search` 工具决定是否检索。`PersistingAgentRuntime` 同步维护消息、Run、事件和用量记录。

## 模块边界

| 模块 | 主要职责 |
|---|---|
| `v5ai-common` | 响应、分页、安全与 AgentScope 运行契约 |
| `v5ai-modules/v5ai-model`、`v5ai-agent` | 模型管理；Agent 编辑、发布与版本 |
| `v5ai-modules/v5ai-rag`、`v5ai-worker` | 知识库、检索与异步文档索引 |
| `v5ai-modules/v5ai-mcp`、`v5ai-skill` | 工具与 Skill 管理及运行时解析 |
| `v5ai-modules/v5ai-runtime` | 对话 API、运行装配、会话与事件持久化 |
| `v5ai-modules/v5ai-platform` | 认证、RBAC、API Key、配额、审计和数据库迁移 |
| `v5ai-modules/v5ai-workflow` | 工作流定义、管理、节点执行和运行记录 |
| `v5ai-api`、`v5ai-starter` | 跨模块 Web 装配与启动入口 |

设计取舍与更细的时序图见仓库 `docs/superpowers/specs/2026-08-12-v5ai-nb-design.md`、`README.md` 和 `docs/adr/`。这些内部文件保留实现过程与决策记录；本网站聚焦可操作的现行用法。
