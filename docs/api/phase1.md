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

`GET /api/admin/models/options` 返回模型下拉选项（`[{ "value": <modelId>, "label": "<providerId>/<modelKey> (<modelType>)" }]`），仅返回启用模型，不加载凭据密文，供 Agent 编辑/绑定弹窗按需加载。

创建 Provider 请求体：`{ "providerKey": "openai", "name": "OpenAI", "enabled": true }`（可选：`description` 提供商描述、`iconUrl` LOGO 图标 URL）；更新通过 `PUT /api/admin/providers`（body 带 `id`），删除通过 `DELETE /api/admin/providers/{ids}`（逗号分隔的 id 列表）。`provider_type` 已移除（V26），运行时按 `providerKey` + 模型的 `adapterKey` 判别 SDK 适配器。

创建 Model 请求体：`{ "providerKey": "openai", "modelKey": "gpt-4o-mini", "type": "CHAT", "modelName": "GPT-4o Mini", "endpoint": "...(可选)", "enabled": true, "credentials": "{\"apiKey\":\"sk-...\"}" }`；扩展字段（均可选）：`description`（模型描述）、`adapterKey`（协议适配器标识，如 openai-compatible/http）、`config`（模型参数配置 JSON，对应 `ModelExtConfigAttrs`：通用 `timeoutMs`/`maxRetries`，CHAT `temperature`/`topP`/`topK`/`maxTokens`/`frequencyPenalty`/`presencePenalty`/`stopSequences`/`seed`/`responseFormat`/`stream`/`extraBody`，EMBEDDING `embeddingDimension`/`encodingFormat`，RERANKER `rerankPath`）、`scope`（`GLOBAL`=全局/`PERSONAL`=个人，缺省 GLOBAL）、`isDefault`（是否默认模型，缺省 false）、`ownerId`（所有者 ID，NULL=全局）。更新通过 `PUT /api/admin/models`（body 带 `id`，`providerKey` 可选，`credentials`/`config` 为空时保留原值），删除通过 `DELETE /api/admin/models/{id}`（被 Agent 引用时返回 409）。

连通性测试 `POST /api/admin/models/{id}/test` 返回 `{ "modelId": <id>, "ok": true|false, "message": "..." }`。

Supported Agent Runtime provider types:

- `OPENAI`
- `OPENAI_COMPATIBLE`
- `DASHSCOPE`

Runtime model credentials are stored encrypted. Before encryption, the credential JSON shape is:

```json
{
  "apiKey": "sk-...",
  "baseUrl": "https://api.openai.com/v1",
  "temperature": 0.7,
  "maxTokens": 2048
}
```

`baseUrl`, `temperature`, and `maxTokens` are optional. `EMBEDDING` and `RERANK` models can be managed, but cannot be used as Agent Runtime chat models.

## Admin Agent APIs

```http
POST /api/admin/agents
GET /api/admin/agents
POST /api/admin/agents/{agentKey}/publish
```

Publishing creates an immutable application version snapshot in `v5ai_application_version` and marks the application as `PUBLISHED`.
