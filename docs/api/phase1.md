# v5ai-nb Phase 1 API

## Authentication

Admin authentication uses Sa-Token JWT.

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin"
}
```

The response shape is:

```json
{
  "tokenType": "Bearer",
  "token": "<jwt>"
}
```

Runtime chat APIs use Agent API Keys. The key is passed as:

```http
Authorization: Bearer <application-api-key>
```

## Streaming Chat

```http
POST /api/v1/agents/{agentKey}/chat/stream
Authorization: Bearer <application-api-key>
Content-Type: application/json
Accept: text/event-stream

{
  "conversationId": "optional-uuid",
  "query": "你好"
}
```

The response is `text/event-stream`. Phase 1 emits platform-owned runtime events:

- `RUN_STARTED`
- `TEXT_DELTA`
- `RUN_COMPLETED`

The API does not expose AgentScope Java implementation classes.

## Blocking Chat

```http
POST /api/v1/agents/{agentKey}/chat
Authorization: Bearer <application-api-key>
Content-Type: application/json

{
  "conversationId": "optional-uuid",
  "query": "你好"
}
```

The response body is:

```json
{
  "runId": "<run-id>",
  "answer": "<assistant-answer>"
}
```

## Phase 1 Notes

- Runtime endpoints require an Agent API Key.
- Requests resolve only published Applications.
- Missing `conversationId` is normalized to a generated UUID in the runtime persistence decorator.
- The current Agent executor returns a deterministic development placeholder. The real AgentScope SDK adapter will replace `AgentTextExecutor` behind the same runtime boundary.
- The `v5ai-ui` frontend calls these APIs through Vite proxy during local development.

## Admin Model APIs

控制器位于 `v5ai-model`（`V5aiModelProviderController` / `V5aiModelController`），统一返回 `R<T>` 包装：

```http
POST   /api/admin/providers
GET    /api/admin/providers?pageNum=&pageSize=
PUT    /api/admin/providers
DELETE /api/admin/providers/{ids}
POST   /api/admin/models
GET    /api/admin/models?pageNum=&pageSize=
PUT    /api/admin/models
DELETE /api/admin/models/{id}
GET    /api/admin/models/options
POST   /api/admin/models/{id}/test
```

Provider rows are stored in `v5ai_model_provider`. Model rows are stored in `v5ai_model`; credentials are encrypted before persistence.

`GET /api/admin/models/options` 返回模型下拉选项（`[{ "value": <modelId>, "label": "<modelId>/<modelName> (<modelType>)" }]`），仅返回启用模型，不加载凭据密文，供 Agent 编辑/绑定弹窗按需加载；模型名称为空时 label 使用 `modelKey`。

创建 Provider 请求体：`{ "providerKey": "openai", "name": "OpenAI", "enabled": true }`（可选：`description` 提供商描述、`iconUrl` LOGO 图标 URL）；更新通过 `PUT /api/admin/providers`（body 带 `id`），删除通过 `DELETE /api/admin/providers/{ids}`（逗号分隔的 id 列表）。`provider_type` 已移除（V26），运行时按 `providerKey` + 模型的 `adapterKey` 判别 SDK 适配器。**停用约束**：`enabled: false` 且该供应商下存在任一模型引用时返回 409（`该供应商下存在模型，无法停用`）；`iconUrl` 传空串（`""`）表示清空图标。

创建 Model 请求体：`{ "providerKey": "openai", "modelKey": "gpt-4o-mini", "type": "CHAT", "modelName": "GPT-4o Mini", "baseUrl": "https://api.deepseek.com/v1(可选)", "enabled": true, "credentials": "{\"apiKey\":\"sk-...\",\"baseUrl\":\"https://...\"}" }`（`baseUrl` 为 API 基础地址，与凭据 JSON 的 `baseUrl` 键一致；V29 起 `endpoint` 字段/列已统一更名为 `baseUrl`）；扩展字段（均可选）：`description`（模型描述）、`adapterKey`（协议适配器标识，如 openai-compatible）、`config`（模型参数配置 JSON，对应 `ModelExtConfigAttrs`：通用 `timeoutMs`/`maxRetries`，CHAT `temperature`/`topP`/`topK`/`maxTokens`/`frequencyPenalty`/`presencePenalty`/`stopSequences`/`seed`/`responseFormat`/`stream`/`extraBody`，EMBEDDING `embeddingDimension`/`encodingFormat`，RERANKER `rerankPath`）、`scope`（`GLOBAL`=全局/`PERSONAL`=个人，缺省 GLOBAL）、`isDefault`（是否默认模型，缺省 false）、`ownerId`（所有者 ID，NULL=全局）。更新通过 `PUT /api/admin/models`（body 带 `id`，`providerKey` 可选，`credentials`/`config` 为空时保留原值），删除通过 `DELETE /api/admin/models/{id}`（被 Agent 或知识库引用时返回 409）。

**模型启用/停用与默认标记（专用端点，UI 行内开关走此处，不走通用更新接口）**：

- `PUT /api/admin/models/{id}/enabled`，body `{ "enabled": boolean }`：切换启用/停用。**停用校验**：模型被任一 Agent（任意状态）或任一知识库（`embedding_model_id`/`rerank_model_id`）引用时返回 409，错误信息含明细（`模型被 X 个 Agent、Y 个知识库使用，无法禁用`）；停用的是默认模型时自动清除其默认标记；幂等（状态未变化返回成功）。
- `PUT /api/admin/models/{id}/default`，body `{ "isDefault": boolean }`：切换默认标记。**同类型唯一**：同 `model_type`（CHAT/EMBEDDING/RERANK）至多一个默认，设为默认时自动清除同类型其它模型的默认；设为默认要求模型已启用（否则 409，`模型未启用，不能设为默认`）；缺省 `isDefault` 视为取消默认。

上述不变式在通用更新接口 `PUT /api/admin/models` 内同样生效（enabled 置 false 触发同一引用校验与默认清理、isDefault 触发同类型唯一清理），无法绕过。

连通性测试 `POST /api/admin/models/{id}/test` 返回 `{ "modelId": <id>, "ok": true|false, "message": "..." }`。测试按模型类型分发：**CHAT** 走对话首块探测；**EMBEDDING** 向 `{baseUrl}/embeddings` 发最小编码请求（`{"model":key,"input":"ping"}`）；**RERANK** 向 `{baseUrl}{rerankPath}`（config 未配时缺省 `/rerank`）发最小重排请求——HTTP 2xx 判定连通，非 2xx/异常返回 `ok:false` 与原因。

Supported Agent Runtime provider types:

- `OPENAI`
- `OPENAI_COMPATIBLE`
- `DASHSCOPE`

Runtime model credentials are stored encrypted. Before encryption, the credential JSON shape is:

```json
{
  "apiKey": "sk-...",
  "baseUrl": "https://api.openai.com/v1"
}
```

`baseUrl` is optional. `temperature`/`maxTokens` 等生成参数不再从凭据读取：一律放在 `config`（`ModelExtConfigAttrs`，config 为运行时生成参数唯一权威来源），凭据只承载连接信息（apiKey/baseUrl）。`EMBEDDING` and `RERANK` models can be managed, but cannot be used as Agent Runtime chat models.

## Admin Agent APIs

```http
POST /api/admin/agents
GET /api/admin/agents
POST /api/admin/agents/{agentKey}/publish
```

`GET /api/admin/agents` 查询参数：

| 参数 | 语义 |
|---|---|
| `keyword` | 模糊关键词（`name` 或 `agentKey` 任一命中，大小写不敏感，OR） |
| `status` | 状态精确匹配（`DRAFT` / `PUBLISHED` / `DISABLED`） |
| `name` / `agentKey` | 保留的精确模糊条件（大小写敏感，AND） |

Publishing creates an immutable application version snapshot in `v5ai_application_version` and marks the application as `PUBLISHED`.
