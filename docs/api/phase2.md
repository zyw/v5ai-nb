# v5ai-nb Phase 2 API（RAG 知识库）

## 知识库管理

```http
POST /api/admin/knowledge-bases
Content-Type: application/json
Authorization: Bearer <admin-token>

PUT /api/admin/knowledge-bases          # 编辑（body 同创建，带 id）
GET /api/admin/knowledge-bases
GET /api/admin/knowledge-bases/options      # 下拉选项 [{ value, label: "name (#id)" }]
DELETE /api/admin/knowledge-bases/{id}        # 禁用
```

创建/编辑请求体（`KnowledgeBaseBo`，创建必填 `embeddingModelId`/`vectorStoreInstanceId`/`dimensionOfVectorModel`）：

```json
{
  "id": 1,
  "name": "产品文档",
  "description": "可选",
  "embeddingModelId": 11,
  "vectorStoreInstanceId": 1,
  "dimensionOfVectorModel": 1536,
  "rerankModelId": null,
  "searchEngineEnable": false,
  "searchEngineInstanceId": null,
  "delimiter": "\n\n",
  "dedupStrategy": 2,
  "dedupAction": 0,
  "config": {
    "chunkParams": { "mode": "length", "maxChunkTokens": 2000, "chunkOverlap": null, "mergeShortSegments": false },
    "parseParams": { "engine": "default" }
  }
}
```

- 向量库实例 `category=1`、搜索引擎实例 `category=2`（启用混合检索时必填），由后端校验分类；
- `config` 为 `RagConfigDO`（searchParams/modelParams/chunkParams/parseParams），切片模式
  length/delimiter/regex/smart；`delimiter` 列缺省 `\n\n`，去重策略/冲突动作缺省 2/0；
- Worker 索引时按知识库 `config.chunkParams` 与 `delimiter` 执行切片（智能切片未接入对话模型时回退段落切分）。

`/options` 仅查 id/name 列（不加载描述），供 Agent 编辑/绑定弹窗按需加载。

## 存储实例管理（向量库 / 搜索引擎）

```http
GET    /api/admin/store-instances                 # 分页列表（name 模糊，category/type/status 精确）
GET    /api/admin/store-instances/{id}            # 详情
POST   /api/admin/store-instances                 # 新增
PUT    /api/admin/store-instances                 # 修改
DELETE /api/admin/store-instances/{ids}           # 批量删除（逗号分隔）
```

请求体（`StoreInstanceBo`）：

```json
{
  "id": 1,
  "name": "pg-vector-main",
  "category": 1,
  "type": 1,
  "config": "{\"url\":\"http://localhost:5432\",\"database\":\"v5ai_nb\",\"dimension\":1536}",
  "status": 1,
  "isDefault": true
}
```

- `category`：1-向量库 2-搜索引擎；`type`：1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-PG_FULLTEXT；
  向量库仅允许 PG_VECTOR/MILVUS，搜索引擎仅允许 ELASTICSEARCH/PG_FULLTEXT，`config` 必须为合法 JSON；
- `status`：0-停用 1-启用（新增缺省为 1）；
- `isDefault`：为 `true` 时同分类下其它实例自动取消默认（事务内保证每分类至多一个默认实例）；
- 权限标识：`rag:store:list/query/add/edit/remove`，写操作记录操作日志。

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

## Agent 绑定知识库

```http
POST /api/admin/agents/{agentKey}/knowledge-bindings
Content-Type: application/json

{ "knowledgeBaseIds": [1, 2] }

GET /api/admin/agents/{agentKey}/knowledge-bindings
```

运行时只检索**已绑定**知识库的切片。

## API Key 管理

```http
POST   /api/admin/agents/{agentKey}/api-keys   # 生成新 Key（仅返回一次，BCrypt 存哈希）
GET    /api/admin/api-keys
DELETE /api/admin/agents/{agentKey}/api-keys
```

## 运行时（RAG 注入）

`POST /api/v1/agents/{agentKey}/chat/stream` 会自动：

1. 解析已发布 Agent；
2. 查询绑定的知识库，对用户问题做向量检索（无 EMBEDDING 模型时回退 Hash 嵌入；向量为空回退关键词检索）；
3. 命中时先发出 `RETRIEVAL` 事件（payload 为引用上下文），再把上下文注入 System Prompt；
4. 之后发出 `MODEL_CALL` / `TEXT_DELTA` / `MESSAGE_COMPLETED` / `RUN_COMPLETED`（失败为 `RUN_FAILED`）。

## 会话历史与恢复

```http
GET  /api/v1/agents/{agentKey}/chat/conversations/{conversationId}   # 历史消息列表
POST /api/v1/agents/{agentKey}/chat/conversations/{conversationId}/resume
Content-Type: application/json

{ "query": "继续上一个问题" }
```

恢复调用会携带历史消息作为上下文重跑，并继续持久化新消息与 AgentState。
