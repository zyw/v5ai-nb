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

Runtime chat APIs use Application API Keys. The key is passed as:

```http
Authorization: Bearer <application-api-key>
```

## Streaming Chat

```http
POST /api/v1/apps/{appKey}/chat/stream
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
POST /api/v1/apps/{appKey}/chat
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

- Runtime endpoints require an Application API Key.
- Requests resolve only published Applications.
- Missing `conversationId` is normalized to a generated UUID in the runtime persistence decorator.
- The current Agent executor returns a deterministic development placeholder. The real AgentScope SDK adapter will replace `AgentTextExecutor` behind the same runtime boundary.
- The `v5ai-ui` frontend calls these APIs through Vite proxy during local development.

## Admin Model APIs

```http
POST /api/admin/providers
GET /api/admin/providers
POST /api/admin/models
GET /api/admin/models
```

Provider rows are stored in `v5ai_model_provider`. Model rows are stored in `v5ai_model`; credentials are encrypted before persistence.

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

## Admin Application APIs

```http
POST /api/admin/applications
GET /api/admin/applications
POST /api/admin/applications/{appKey}/publish
```

Publishing creates an immutable application version snapshot in `v5ai_application_version` and marks the application as `PUBLISHED`.
