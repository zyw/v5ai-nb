# v5ai-nb Phase 3 API（MCP）

## MCP Server 管理

```http
POST /api/admin/mcp-servers
Content-Type: application/json
Authorization: Bearer <admin-token>

{
  "name": "weather-mcp",                 # 平台内唯一（也是 MCP 客户端名）
  "transportType": "STREAMABLE_HTTP",    # STREAMABLE_HTTP | SSE | STDIO
  "endpoint": "https://mcp.example.com/mcp",  # HTTP/SSE 填 URL；STDIO 填可执行命令
  "args": ["-m", "mcp_server_git"],      # STDIO 参数（可选）
  "headers": { "Authorization": "Bearer xxx" },  # HTTP 请求头（加密存储）
  "envVars": { "API_KEY": "xxx" },       # STDIO 环境变量（加密存储）
  "timeoutSeconds": 30                   # 默认 30
}

GET  /api/admin/mcp-servers
GET  /api/admin/mcp-servers/options           # 下拉选项 [{ value, label: "name (#id)" }]
PUT  /api/admin/mcp-servers/{id}
DELETE /api/admin/mcp-servers/{id}       # 禁用（逻辑删除）
PUT  /api/admin/mcp-servers/{id}/enable  # 启用（与禁用互逆）
```

`/options` 仅查 id/name 列（不解密加载请求头/环境变量密文），供 Agent 编辑/绑定弹窗按需加载。

Stdio 命令受白名单限制（`v5ai.mcp.stdio-command-whitelist`，逗号分隔）；
命令可以是 PATH 上的纯可执行文件名（`node`），也可以是可执行文件路径（`D:/nvm/v24.9.0/node.exe`），
但禁止 `..` 路径穿越。白名单项写成纯文件名时按可执行文件名匹配（`node.exe` 可匹配任意路径下的 node.exe），
写成路径时按完整路径匹配；白名单为空表示开发模式不限制。

## 连接测试

```http
POST /api/admin/mcp-servers/{id}/test-connection

{
  "serverId": 1,
  "ok": true,
  "message": "connection ok",
  "toolCount": 3
}
```

测试结果会记录到 Server（`lastTestStatus/lastTestMessage/lastTestedAt`）。

## Tool 发现

```http
POST /api/admin/mcp-servers/{id}/discover-tools   # 连接并拉取服务器工具清单，写入 v5ai_mcp_tool
GET  /api/admin/mcp-servers/{id}/tools            # 查看已缓存的工具
```

- 工具元数据（描述、输入 JSON Schema、只读提示）缓存于 `v5ai_mcp_tool`；
- 默认权限：只读工具 `ALLOW`，可写工具 `APPROVE`（发现刷新会保留管理员调整过的权限）；
- 再次发现会清理服务器已下线的工具。

## Tool 权限

```http
PUT /api/admin/mcp-servers/{id}/tools/{toolName}/permission
Content-Type: application/json

{ "permission": "DENY" }    # ALLOW | APPROVE | DENY
```

三态决策：

- `ALLOW`：直接执行；
- `APPROVE`：运行时先发 `PERMISSION_REQUIRED` 事件（人工审批提示），随后自动放行；
- `DENY`：运行时**不注册**该工具（模型不可见，调用即被拒绝）。

## Agent 绑定

```http
POST /api/admin/agents/{agentKey}/mcp-bindings
Content-Type: application/json

{ "mcpServerIds": [1, 2] }   # 全量替换

GET /api/admin/agents/{agentKey}/mcp-bindings
```

绑定前会校验 Server 存在且处于 ACTIVE 状态。运行时只解析**已绑定** Server 的
已发现工具（DENY 过滤），并按 Agent 已发布配置建立隔离的 Tool 集合。

## 运行时（动态 Tool 注册）

`POST /api/v1/agents/{agentKey}/chat/stream` 在 Agent 绑定了 MCP Server 时：

1. 解析已发布 Agent 的 MCP 绑定；
2. 为每个 ACTIVE Server 建立独立连接，读取缓存的 Tool 元数据（DENY 过滤）；
3. 把工具动态注册到 AgentScope Toolkit（HarnessAgent）；
4. 模型发起工具调用时发出 `TOOL_CALL` 事件，返回结果发出 `TOOL_RESULT` 事件
   （`APPROVE` 权限先发 `PERMISSION_REQUIRED`）；
5. 每次调用落 `v5ai_mcp_tool_call` 审计（runId、工具名、参数摘要、决策、状态、耗时）。

单个 Server 连接失败时跳过该 Server，不中断整个运行。

## 审计查询

```http
GET /api/admin/mcp-servers/{id}/tool-calls                  # 按 Server
GET /api/admin/mcp-tool-calls?runId=<run-id>                # 按运行
GET /api/admin/mcp-tool-calls?serverId=<id>                 # 按 Server

[
  {
    "id": 1,
    "runId": "uuid",
    "serverId": 1,
    "serverName": "weather-mcp",
    "toolName": "get_weather",
    "argumentsSummary": "{city=Berlin}",
    "decision": "ALLOW",
    "status": "SUCCESS",
    "durationMs": 234,
    "message": null,
    "createdAt": "..."
  }
]
```

## 典型使用流程

```text
创建 MCP Server → 测试连接 → 发现工具 → 调整权限（可选）
→ Agent 绑定 MCP Server → 发布 Agent → SSE 对话触发工具调用 → 查询审计
```
