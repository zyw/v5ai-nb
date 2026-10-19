# API 与鉴权

平台区分管理 API 和运行 API。管理 API 由 Sa-Token 登录保护；Agent 运行 API 使用单独签发的 API Key，并按绑定关系、限流与配额校验。

## 鉴权方式

| 路径 | 凭证 | 用途 |
|---|---|---|
| `/api/admin/**` | `Authorization: Bearer <access_token>` | 管理端资源操作；写操作需要 `ADMIN` 角色 |
| `/api/v1/agents/**` | `Authorization: Bearer <agent-api-key>` | 对话门户和外部应用调用已发布 Agent |
| `/api/v1/workflows/**` | Agent API Key | 调用已发布工作流；Key 需允许工作流引用的 Agent |
| `/api/health` | 无 | 健康检查 |

管理端登录与刷新接口默认使用 RSA+AES 混合加密，直接提交明文 JSON 会被拒绝。前端已经内置这一步；需要自建管理客户端时请参照仓库 `docs/api/phase5.md` 和 `v5ai-ui/src/utils/crypto.ts`。

多数管理响应使用统一的 `R<T>`：`{ "code": 200, "msg": "...", "data": ... }`。SSE 对话返回 `text/event-stream`；阻塞对话返回包含 `runId`、`answer` 的结果。不要仅依赖 HTTP 200 判断业务成功，还应读取 body 中的 `code`。

## 流式对话

先发布 Agent，并为它创建及绑定 API Key。然后调用：

```bash
curl -N 'http://localhost:8080/api/v1/agents/demo/chat/stream' \
  -H 'Authorization: Bearer <agent-api-key>' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"query":"你好"}'
```

`conversationId` 可选；留空时服务端生成新会话。请求还支持 `webSearch`、`attachments`、`disabledMcpServerIds` 和 `disabledSkillIds`，后两个字段只会收窄 Agent 已绑定的能力。

```http
POST /api/v1/agents/{agentKey}/chat/stream  # SSE
POST /api/v1/agents/{agentKey}/chat         # 等待完整回答
```

SSE 事件包括 `RUN_STARTED`、`MODEL_CALL`、`TEXT_DELTA`、`REASONING_DELTA`、`TOOL_CALL`、`TOOL_RESULT`、`RETRIEVAL`、`MESSAGE_COMPLETED`、`RUN_COMPLETED` 和失败时的 `RUN_FAILED`。具体载荷见仓库 `README.md` 与 `docs/api/phase6.md`。

## 对话门户接口

```http
GET   /api/v1/agents/auth/bootstrap
GET   /api/v1/agents/{agentKey}/chat/conversations
GET   /api/v1/agents/{agentKey}/chat/conversations/{conversationId}
PATCH /api/v1/agents/{agentKey}/chat/conversations/{conversationId}
POST  /api/v1/agents/{agentKey}/chat/conversations/{conversationId}/regenerate
POST  /api/v1/agents/{agentKey}/chat/runs/{runId}/stop
POST  /api/v1/agents/resource/upload
GET   /api/v1/agents/attachments/{resourceId}
```

会话与附件按 API Key 归属隔离。会话已归档时继续对话返回 409；Key 无效返回 401，未绑定目标 Agent 返回 403，超限返回 429。完整请求体与响应示例见 `docs/api/phase6.md`。

## 管理 API 速查

| 能力 | 路径前缀 |
|---|---|
| Provider / Model | `/api/admin/providers`、`/api/admin/models` |
| Agent / 发布 | `/api/admin/agents`、`/{agentKey}/publish` |
| 知识库 / 文档 | `/api/admin/knowledge-bases`、`/api/admin/knowledge-bases/{kbId}/documents` |
| MCP | `/api/admin/mcp-servers`、`/api/admin/mcp-tool-calls` |
| Skill | `/api/admin/skills` |
| Workflow | `/api/admin/workflows` |
| 用户、配额、用量与审计 | `/api/admin/users`、`/api/admin/apps/{agentKey}/quota`、`/api/admin/usage`、`/api/admin/logs` |

路径和参数以各模块 Controller 为准；仓库 `docs/api/phase1.md` 至 `phase6.md` 给出分阶段契约，早期示例可能仍保留当时的响应形态。
