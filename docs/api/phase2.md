# v5ai-nb Phase 2 API（RAG 知识库）

## 知识库管理

```http
POST /api/admin/knowledge-bases
Content-Type: application/json
Authorization: Bearer <admin-token>

{ "name": "产品文档", "description": "可选" }

GET /api/admin/knowledge-bases
DELETE /api/admin/knowledge-bases/{id}        # 禁用
```

## 文档管理

```http
POST /api/admin/knowledge-bases/{kbId}/documents
Authorization: Bearer <admin-token>
Content-Type: multipart/form-data
# file=guide.md（.txt/.md/.pdf/.docx）, title=可选

POST /api/admin/knowledge-bases/{kbId}/documents/url
Content-Type: application/json

{ "url": "https://example.com/doc", "title": "可选" }

GET  /api/admin/knowledge-bases/{kbId}/documents
DELETE /api/admin/documents/{id}
POST  /api/admin/documents/{id}/retry          # 重置任务为 PENDING
GET  /api/admin/knowledge-bases/{kbId}/tasks   # 索引任务（含状态/尝试次数/错误）
```

文档状态：`PENDING → PROCESSING → COMPLETED | FAILED`（Worker 异步处理，最多重试 3 次）。

## Application 绑定知识库

```http
POST /api/admin/applications/{appKey}/knowledge-bindings
Content-Type: application/json

{ "knowledgeBaseIds": [1, 2] }

GET /api/admin/applications/{appKey}/knowledge-bindings
```

运行时只检索**已绑定**知识库的切片。

## API Key 管理

```http
POST   /api/admin/applications/{appKey}/api-keys   # 生成新 Key（仅返回一次，BCrypt 存哈希）
GET    /api/admin/api-keys
DELETE /api/admin/applications/{appKey}/api-keys
```

## 运行时（RAG 注入）

`POST /api/v1/apps/{appKey}/chat/stream` 会自动：

1. 解析已发布 Application；
2. 查询绑定的知识库，对用户问题做向量检索（无 EMBEDDING 模型时回退 Hash 嵌入；向量为空回退关键词检索）；
3. 命中时先发出 `RETRIEVAL` 事件（payload 为引用上下文），再把上下文注入 System Prompt；
4. 之后发出 `MODEL_CALL` / `TEXT_DELTA` / `MESSAGE_COMPLETED` / `RUN_COMPLETED`（失败为 `RUN_FAILED`）。

## 会话历史与恢复

```http
GET  /api/v1/apps/{appKey}/chat/conversations/{conversationId}   # 历史消息列表
POST /api/v1/apps/{appKey}/chat/conversations/{conversationId}/resume
Content-Type: application/json

{ "query": "继续上一个问题" }
```

恢复调用会携带历史消息作为上下文重跑，并继续持久化新消息与 AgentState。
