# Phase 3 Verification（MCP）

验证时间：2026-08-15

## 命令

```bash
# JDK 21（本项目要求）
set JAVA_HOME=D:\softwares\jdk\bellsoft-jdk-21.0.8+12
mvn clean verify -Dsurefire.failIfNoSpecifiedTests=false
cd v5ai-ui && npx vue-tsc --noEmit   # 前端类型检查
```

结果：后端 `verify` exit 0（新增 24 个测试，全部通过），前端 `vue-tsc --noEmit` exit 0。

## 本轮完成范围（MCP Phase 3）

### 新模块 v5ai-mcp（领域层，零传输库依赖）

- 领域模型：`McpServer`（传输类型/Endpoint/参数/Headers/环境变量/超时/状态/最近测试）、
  `McpToolDefinition`（缓存工具元数据 + 权限）、`McpToolCallAuditDTO`（调用审计）、
  `McpToolPermission`（ALLOW/APPROVE/DENY 三态）、`McpConnectionTestResult`。
- 端口：`McpServerRepository`、`McpToolRepository`、`ApplicationMcpBindingRepository`、
  `McpToolCallAuditRepository`、`McpClientFactory`、`McpConnection`、`StdioCommandPolicy`。
- `McpManagementService`：Server CRUD（禁用即逻辑删除）、连接测试（记录最近结果）、
  Tool 发现（upsert + 保留自定义权限 + 清理下线工具）、Agent 绑定校验、
  权限调整、审计查询。默认权限：只读 `ALLOW`、可写 `APPROVE`。

### 基础设施（AgentScope McpClientBuilder 传输层）

- `V5__mcp_schema.sql`：`v5ai_mcp_server`（headers/env 密文列）、`v5ai_mcp_tool`
  （(server_id, tool_name) 唯一 + permission）、`v5ai_application_mcp`（agent_key 绑定）、
  `v5ai_mcp_tool_call`（run_id/server_id 索引审计表）。
- `AgentScopeMcpClientFactory` / `AgentScopeMcpConnection`：Streamable HTTP / SSE / Stdio
  三种传输；Stdio 受 `PropertyStdioCommandPolicy` 白名单校验（白名单项可为纯文件名或完整路径，
  并禁止 `..` 路径穿越）。
- 加密：headers/envVars 经 `CredentialCipher`（AES-GCM）加密落库，读取时解密。
- `ApplicationMcpToolResolver`：按已发布 Agent 绑定解析 ACTIVE Server 的
  已发现工具（DENY 过滤），单 Server 失败跳过不中断运行。

### 运行时（动态 Tool 注册 + 权限 + 审计）

- `AgentTextEvent` 新增 `ToolCall`/`ToolResult`；`AgentRunBo` 新增 `runId`
  （运行时生成，工具审计据此关联）。
- `ModelStreamTextExecutor` 升级：无工具走原轻量 `Model.stream` 路径；
  有工具时把 `PlatformMcpTool` 动态注册进 HarnessAgent Toolkit，
  订阅 `streamEvents` 并把 `ToolCallStartEvent`/`ToolResultEndEvent` 映射为平台事件。
- `PlatformMcpTool extends ToolBase`：执行前按权限决策（DENY 拒绝并审计），
  每次调用落审计（runId、工具名、参数摘要截断、决策、状态、耗时），
  成功/失败显式标记 `ToolResultState`。
- `AgentScopeRuntime` 事件映射：`TOOL_CALL`（含权限）、`TOOL_RESULT`、
  `APPROVE` 决策先发 `PERMISSION_REQUIRED` 后自动放行。

### Admin API + 前端

- `McpServerController`（/api/admin/mcp-servers CRUD + test-connection + discover-tools +
  tools + permission + tool-calls）、`ApplicationMcpBindingController`（mcp-bindings）、
  `McpToolCallController`（按 runId/serverId 查审计）。
- 前端：`McpServersView`（创建 Server、测试连接、发现工具、权限下拉、Agent 绑定）、
  router 与菜单注册；`client.ts` 补齐全部 MCP API 类型与函数。

## 说明 / 限制

- Stdio 白名单默认空（开发模式不限制，启动打 WARN）；生产必须设置
  `V5AI_MCP_STDIO_COMMAND_WHITELIST`。
- 运行时使用缓存的 Tool 元数据：先"发现工具"再"绑定 + 发布"，运行时才有工具可用。
- `APPROVE` 目前为"发事件 + 自动放行"，交互式人工审批流（挂起等待确认）留待
  Phase 5（AgentState/审批）接入。
- 单实例部署下 MCP 连接为每次运行建立（隔离 Tool 集合）；连接池复用留待后续优化。
