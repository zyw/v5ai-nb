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
- **维度校验（保存时权威执行）**：`dimensionOfVectorModel` 须 ≤ `min(模型维度, 向量库上限)`；
  固定维度模型（config 未声明 `dimensionAdjustable=true`）的冻结维度必须等于模型输出维度，否则拒绝；
  引擎类型默认上限：pgvector 2000 / Milvus 32768 / Elasticsearch 2048（实例 config `maxDimension` 可覆盖）。
  pgvector 默认 2000 对应其索引维度上限（ivfflat 恒 2000；hnsw 依部署版本，0.5.x 亦 2000）：
  高维模型须部署 hnsw 支持该维度的 pgvector 并在实例 config `maxDimension` 显式覆盖；
  索引创建失败时平台自动降级为无索引精确检索（结果正确，大数据量需升级引擎后手动补索引）。

### 维度检查（切换嵌入模型/向量库时实时查询）

```http
GET /api/admin/knowledge-bases/dimension-check?embeddingModelId=11&vectorStoreInstanceId=1
Authorization: Bearer <admin-token>
```

```json
{
  "modelMaxDimension": 1024,       // 模型 config 声明（固定输出或可调上限）；未声明且探测失败为 null
  "storeMaxDimension": 2000,       // 引擎类型默认或实例 config maxDimension 覆盖
  "effectiveMaxDimension": 1024,   // min(model, store)：当前可用上限
  "dimensionAdjustable": false     // 模型是否支持 dimensions 参数降维
}
```

- UI 语义：`dimensionAdjustable=true` 时 `effectiveMaxDimension` 为可选上限（可冻结更低维度）；
  `false` 且 `effective < modelMax` 的组合不存在（保存时被权威校验拒绝）；
- 用于 KB 创建/编辑表单在切换模型或向量库时回填「向量维度」并即时提示。

`/options` 仅查 id/name 列（不加载描述），供 Agent 编辑/绑定弹窗按需加载。

## 存储实例管理（向量库 / 搜索引擎）

```http
GET    /api/admin/store-instances                 # 分页列表（name 模糊，category/type/status 精确）
GET    /api/admin/store-instances/{id}            # 详情
POST   /api/admin/store-instances                 # 新增
PUT    /api/admin/store-instances                 # 修改
DELETE /api/admin/store-instances/{ids}           # 批量删除（逗号分隔）
POST   /api/admin/store-instances/test-connection # 连接测试（type + config + 可选 id）
PUT    /api/admin/store-instances/{id}/default    # 切换默认（body: { "isDefault": true|false }）
```

连接测试请求体（`StoreConnectionTestBo`）：`{ "type": 1, "config": "{...}", "id": 1 }`。`id` 仅编辑态传入，用于合并库中已脱敏的敏感字段（`password`/`token` 留空沿用原值）。支持全部类型：1-PG_VECTOR / 4-PG_FULLTEXT 走 JDBC `SELECT 1`（PG_VECTOR 额外校验 `pgvector` 扩展），2-MILVUS 走 `listCollections`，3-ELASTICSEARCH 走 `GET /`。响应 `{ ok, message }` 始终 200，不落库测试结果。

> 运行时存储后端（Phase 2 增强）：知识库按 `vectorStoreInstanceId` 路由向量写入/检索（PG pgvector / Milvus / Elasticsearch kNN），按 `searchEngineInstanceId`（启用 `searchEngineEnable` 时）双写并路由关键词检索（ES match / PG ILIKE）；Milvus 不提供关键词，PG_FULLTEXT 本期未接线。实现位于 `v5ai-common-elasticsearch`、`v5ai-common-milvus`（客户端+配置+连接测试）与 `v5ai-rag` 的 `VectorStoreResolver` / `ElasticsearchVectorStore` / `MilvusVectorStore`。

请求体（`StoreInstanceBo`）：

```json
{
  "id": 1,
  "name": "pg-vector-main",
  "description": "生产环境主向量库",
  "category": 1,
  "type": 1,
  "config": "{\"host\":\"localhost\",\"port\":5432,\"database\":\"v5ai_ai\",\"username\":\"postgres\",\"sslEnabled\":false}",
  "status": 1,
  "isDefault": true
}
```

- `description`：实例描述（可选）；查询接口返回时 `config` 中的敏感字段（`password`/`token`）会被移除，编辑提交时敏感字段为空则保留库中原值、非空则替换；
- `category`：1-向量库 2-搜索引擎；`type`：1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-PG_FULLTEXT；
  向量库支持 PG_VECTOR/MILVUS/ELASTICSEARCH，搜索引擎支持 ELASTICSEARCH/PG_FULLTEXT，`config` 必须为合法 JSON；
- `status`：0-停用 1-启用（新增缺省为 1）；
- `isDefault`：为 `true` 时同分类下其它实例自动取消默认（事务内保证每分类至多一个默认实例）；
- 切换默认：`PUT /{id}/default`（请求体 `{ "isDefault": true|false }`），`true` 设为默认并清除同分类其它默认，`false` 仅取消自身默认（分类可暂无默认）；权限 `rag:store:edit`；
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

文档状态：`0-待处理 → 1-解析中 → 2-处理中 → 3-处理完成 | 4-处理失败`（Worker 异步处理，最多重试 3 次）；
`sourceType` 标识来源（`UPLOAD`=上传 / `URL`=网络），另含存储元数据列
（`storageType`/`storagePath`/`fileSize`/`chunkCount`/`parseTime`/`contentHash`）与资源库关联列 `resourceId`（见迁移 V25）。

分片业务表 `v5ai_knowledge_chunk`（迁移 V24 扩展 + V30 存储解耦改造）：
- 内容/元数据列：`paragraph_index`（段落索引）、`token_count`（分片 token 数量）、`vector_id`（向量 id）、
  `content_hash`（chunk 内容 SHA-256，用于向量去重）、`source_type`（TEXT=文本 / IMAGE=图片）、`updated_at`（更新时间），
  并新增 `(knowledge_base_id)`、`(knowledge_base_id, content_hash)`、`(knowledge_base_id, source_type)` 组合索引；
- **向量与业务行解耦（V30）**：`embedding` 列与 ivfflat 索引已删除，向量落在知识库绑定的存储实例集合
  （pgvector=每知识库独立向量表 `v5ai_vec_{kbId}` / Milvus=collection / ES=index），集合主键 = 业务生成的
  `vector_id`(UUID，唯一索引)；切片内容/元数据以业务行为准，向量检索召回 `(vector_id, score)` 后回业务表批量取内容；
- 存量处理：V30 清空存量 chunk 数据，存量文档行保留、索引任务重置为 PENDING 由 Worker 重建（不做数据迁移）；
- Worker 写入时按空白切分估算 `token_count` 并计算 `content_hash`，同时写业务行（含 `vector_id`）与存储实例集合向量。

## 知识库详情页（调试：切片 / 知识检索 / 知识问答）

从知识库列表点库名进入的站内详情页（前端路由 `knowledge-base-detail`），头部 + 四个 tab：
原始文档（复用上面文档管理接口）/ 切片详情 / 知识检索 / 知识问答。文档管理能力已迁入详情页，列表页不再内联管理。

```http
GET /api/admin/knowledge-bases/{id}                    # 详情：KnowledgeBaseVo + docCount/chunkCount

# 切片管理（卡片流 / 筛选 / 手工切片 CRUD，写操作同步维护业务行与存储实例集合向量）
GET    /api/admin/knowledge-bases/{kbId}/chunks        # 分页（documentId / chunkId / content 过滤）
POST   /api/admin/knowledge-bases/{kbId}/chunks        # { documentId, content } → 业务行落库（vector_id=UUID）并写集合向量
PUT    /api/admin/knowledge-bases/{kbId}/chunks/{chunkId}   # { content } → 更新业务行并按 vector_id 重嵌集合向量
DELETE /api/admin/knowledge-bases/{kbId}/chunks/{chunkId}   # 删除业务行并移除集合内该 vector_id 的向量

# 检索调试
POST /api/admin/knowledge-bases/{kbId}/retrieve
Content-Type: application/json

# 问答调试（SSE，事件名 = RuntimeEventType：RETRIEVAL / TEXT_DELTA / RUN_COMPLETED / RUN_FAILED）
POST /api/admin/knowledge-bases/{kbId}/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

`retrieve` 请求体（空字段按知识库 `config.searchParams` → 默认值补齐，仅本次会话生效、不回写）：

```json
{
  "query": "单晶硅如何拉晶",
  "resultCount": 20,
  "questionRewrite": false,
  "thresholdEnabled": false,
  "threshold": 0.5,
  "fusionStrategy": "RRF",
  "rrfK": 60,
  "modelId": 3
}
```

- 融合策略：`RRF`（倒数排名融合，默认）/ `VECTOR` / `KEYWORD`；向量路得分 = 余弦相似度（0~1），关键词路为 ILIKE 粗检（得分 1.0）；
- 阈值过滤仅作用于向量相似度；RRF 两路候选数 = `max(resultCount*3, 30)`，按 `sum(1/(rrfK+rank))` 融合后取前 `resultCount`；
- 问题改写为「半真」：需要 `modelId`（或知识库 `config.modelParams.modelId`），模型缺失/调用失败时自动跳过改写并走原问题，不影响检索；
- `chat/stream` 请求体为 `KbChatBo`：`modelId` / `nearbySliceCount`（拼接邻近文本片数量，默认 5，= 每个命中前后补片数）/ `prompt`（可含 `<Documents>` 变量，缺省用内置模板）/ 检索参数同 retrieve / `messages`（`[{role: user|assistant, content}]`，末条为当前问题）。
  问答链路：检索 → 邻近补全 → Prompt 注入 → 对话模型流式回答；**无状态**（历史由前端携带，不落库、不占运行配额、不计 Agent 用量），对话模型经运行时 `AgentModelResolver` 解析（仅 CHAT 模型可用）。

注意事项：
- 手工切片写入与 Worker 索引共用同一条 Embedding→VectorStore 链路；单切片编辑/删除为行级操作，仅默认 PgVectorStore 保证索引同步（ES/Milvus 实例无单条更新 API，编辑后需重新索引文档保持一致）；
- 切片列表不返回向量列；命中/引用载荷里切片内容截断至 2000 字符；
- 知识库被禁用不影响详情浏览；切片写入不校验知识库状态（与文档上传一致）。

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
