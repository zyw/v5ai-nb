# 连接 MCP 工具

MCP Server 让 Agent 调用外部工具。平台支持 Streamable HTTP、SSE 和 Stdio 连接方式，并保存发现的工具元数据与调用审计。

## 接入流程

1. 在 **MCP 管理**注册 Server，填写名称、传输方式、地址或命令。
2. 执行 **测试连接**，确认 Server 可达。
3. 点击 **发现工具**，把工具名、描述与输入 Schema 缓存到平台。
4. 为每个工具设置权限，再把 Server 绑定到 Agent。
5. 在 Agent 中启用 MCP 能力，保存并发布后调试调用过程。

::: warning 先发现，再绑定使用
运行时解析的是已发现且未被拒绝的工具。只完成连接测试而没有发现工具，Agent 仍没有可用的 MCP Tool。
:::

## 工具权限

| 权限 | 行为 |
|---|---|
| `ALLOW` | 允许执行 |
| `APPROVE` | 先发出 `PERMISSION_REQUIRED` 事件，当前实现随后自动放行；它不是阻塞等待人工批准的流程 |
| `DENY` | 运行时不注册该工具，模型不可见 |

工具调用时会有 `TOOL_CALL` / `TOOL_RESULT` 事件，并记录 runId、工具名、参数摘要、决策、状态和耗时。单个 MCP Server 连接失败时，该 Server 会被跳过，不会中断整个 Agent 运行。

## 连接凭据与 Stdio

HTTP headers 和 Stdio 环境变量加密存储。生产部署的 Stdio 命令必须按 `V5AI_MCP_STDIO_COMMAND_WHITELIST` 设置白名单；可写可执行文件名或完整路径。不要把密钥写入工具描述或提示词。

## 相关 API

```http
POST /api/admin/mcp-servers
POST /api/admin/mcp-servers/{id}/test-connection
POST /api/admin/mcp-servers/{id}/discover-tools
PUT  /api/admin/mcp-servers/{id}/tools/{toolName}/permission
POST /api/admin/agents/{agentKey}/mcp-bindings
GET  /api/admin/mcp-tool-calls
```

详细契约见仓库 `docs/api/phase3.md`，实现入口在 `v5ai-modules/v5ai-mcp`。
