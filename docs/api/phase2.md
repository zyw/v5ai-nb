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
    "chunkParams": { "sliceStrategy": "length", "maxChunkLength": 2000, "chunkOverlap": null, "mergeShortSegments": false },
    "parseParams": { "engine": "default" }
  }
}
```

- 向量库实例 `category=1`、搜索引擎实例 `category=2`（启用混合检索时必填），由后端校验分类；
- `config` 为 `RagConfigDO`（searchParams/modelParams/chunkParams/parseParams），切片策略
  length/delimiter/regex/smart；`delimiter` 列缺省 `\n\n`，去重策略/冲突动作缺省 2/0；
- Worker 索引时按知识库 `config.chunkParams` 与 `delimiter` 执行切片：每种切片策略由独立实现类负责一级切分（按长度/智能按空行粗分段落、按分隔符/正则按其规则），再经共享流水线按最大长度切分（带重叠）；
  智能切片接入对话模型时改由模型语义切分，调用失败回退段落切分。
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

> 运行时存储后端（Phase 2 增强）：知识库按 `vectorStoreInstanceId` 路由向量写入/检索（PG pgvector / Milvus / Elasticsearch kNN），按 `searchEngineInstanceId`（启用 `searchEngineEnable` 时）双写并路由关键词检索（ES match / PG jieba 分词 + Okapi BM25，查询文本无法分词时回退 ILIKE 粗检）；Milvus 不提供关键词，PG_FULLTEXT 本期未接线。实现位于 `v5ai-common-elasticsearch`、`v5ai-common-milvus`（客户端+配置+连接测试）与 `v5ai-rag` 的 `VectorStoreResolver` / `ElasticsearchVectorStore` / `MilvusVectorStore` / `PgBm25KeywordSearch`。

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
GET  /api/admin/parser-engines/health
DELETE /api/admin/documents/{id}
POST  /api/admin/documents/{id}/retry          # 重置任务为 PENDING
GET  /api/admin/knowledge-bases/{kbId}/tasks   # 索引任务（含状态/尝试次数/错误）
```

文档状态：`0-待处理 → 1-解析中 → 2-处理中 → 3-处理完成 | 4-处理失败`（Worker 异步处理，最多重试 3 次）；
`sourceType` 标识来源（`UPLOAD`=上传 / `URL`=网络），另含存储元数据列
（`storageType`/`storagePath`/`fileSize`/`chunkCount`/`parseTime`/`contentHash`）与资源库关联列 `resourceId`（见迁移 V25）。新增 `parseEngine`、`parseDiagnostics` 保存实际解析引擎与文档级诊断（迁移 V47）。
知识库 `config.parseParams.engine` 支持 `default`、`docling`、`mineru`；外部服务不可用、异常、超时或结果为空时记录 WARN 并自动回退 `default`。服务地址、认证和 headers 仅由应用部署配置提供，不从知识库 JSON 或前端提交。

分片业务表 `v5ai_knowledge_chunk`（迁移 V24 扩展 + V30 存储解耦改造 + V31 关键词索引）：
- 内容/元数据列：`paragraph_index`（段落索引）、`token_count`（分片 token 数量）、`vector_id`（向量 id）、
  `content_hash`（chunk 内容 SHA-256，用于向量去重）、`source_type`（TEXT=文本 / IMAGE=图片）、`updated_at`（更新时间），
  并新增 `(knowledge_base_id)`、`(knowledge_base_id, content_hash)`、`(knowledge_base_id, source_type)` 组合索引；
- **向量与业务行解耦（V30）**：`embedding` 列与 ivfflat 索引已删除，向量落在知识库绑定的存储实例集合
  （pgvector=每知识库独立向量表 `v5ai_vec_{kbId}` / Milvus=collection / ES=index），集合主键 = 业务生成的
  `vector_id`(UUID，唯一索引)；切片内容/元数据以业务行为准，向量检索召回 `(vector_id, score)` 后回业务表批量取内容；
- **关键词索引（V31）**：新增 `keyword_tokens text[]`（jieba INDEX 模式分词，保留重复词 → 词频现算）与 GIN 索引，
  服务 PG 原生关键词路的 BM25 打分；由 Worker 重建与手工切片增改维护（删除随行清理），df/avgdl 检索时按库集合现算；
- 存量处理：V30 清空存量 chunk 数据，存量文档行保留、索引任务重置为 PENDING 由 Worker 重建（不做数据迁移）；
  V31 沿用同一策略重建以填充 `keyword_tokens`（重建窗口内未重建行对关键词路短暂不可见，向量路/RRF 不受影响）；
- Worker 写入时按空白切分估算 `token_count` 并计算 `content_hash`，同时写业务行（含 `vector_id`、`keyword_tokens`）与存储实例集合向量。

## 知识库详情页（调试：切片 / 知识检索 / 知识问答）

从知识库列表点库名进入的站内详情页（前端路由 `knowledge-base-detail`），头部 + 四个 tab：
原始文档（复用上面文档管理接口）/ 切片详情 / 知识检索 / 知识问答。文档管理能力已迁入详情页，列表页不再内联管理。

```http
GET /api/admin/knowledge-bases/{id}                    # 详情：KnowledgeBaseVo + docCount/chunkCount

# 切片管理（卡片流 / 筛选 / 手工切片 CRUD，写操作同步维护业务行、集合向量与所属文档分片数量）
GET    /api/admin/knowledge-bases/{kbId}/chunks        # 分页（documentId / chunkId / content 过滤）
POST   /api/admin/knowledge-bases/{kbId}/chunks        # { documentId（所属文档，必填且须同库）, content } → 业务行落库（vector_id=UUID）并写集合向量
PUT    /api/admin/knowledge-bases/{kbId}/chunks/{chunkId}   # { content } → 更新业务行并按 vector_id 重嵌集合向量
DELETE /api/admin/knowledge-bases/{kbId}/chunks/{chunkId}   # 删除业务行并移除集合内该 vector_id 的向量

# 检索调试
POST /api/admin/knowledge-bases/{kbId}/retrieve
Content-Type: application/json

# 问答调试（SSE，事件名 = RuntimeEventType：RETRIEVAL / REASONING_DELTA / TEXT_DELTA / RUN_COMPLETED / RUN_FAILED）
# data 为 RuntimeEvent JSON 包体（{runId,type,payload,createdAt}），页面取其中 payload：
#   RETRIEVAL.payload = 命中数组 JSON 串；REASONING_DELTA.payload = 思考文本片段；
#   TEXT_DELTA.payload = 回答文本片段；RUN_FAILED.payload = 错误信息
POST /api/admin/knowledge-bases/{kbId}/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

`retrieve` 请求体（除 `query` 必填外均可省；取值按 请求 → 知识库 `config.searchParams` → 内置默认 依次补齐，请求本身不回写配置。知识库默认配置由详情页参数区自动保存维护——`PUT /api/admin/knowledge-bases/{kbId}/config`（500ms 防抖），调试改动即成为该库的检索/问答默认值）：

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

- 融合策略：`RRF`（倒数排名融合，默认）/ `WEIGHTED_SUM`（加权求和）/ `VECTOR`（仅向量）/ `KEYWORD`（仅关键词）；未知取值报 400（不静默降级）。向量路得分 = 余弦相似度（0~1）；关键词路得分按存储实现：pgvector 为 jieba 分词 + Okapi BM25 连续相关度（k1=1.2、b=0.75；查询文本全为停用词/标点时回退 ILIKE 粗检恒 1.0）、Elasticsearch 用 `_score`；
- 参数范围与默认：`resultCount` 1~100（默认 20，越界钳制）；`rrfK` 1~200（默认 60，仅 RRF，越界/非法拒绝）；`denseWeight` 0~1（默认 0.5，仅 WEIGHTED_SUM，关键词权重 = 1 − denseWeight）；`threshold` 0~1（默认 0.5）；
- 阈值过滤仅作用于向量相似度；两路候选数 = `max(resultCount*3, 30)`：RRF 按 `sum(1/(rrfK+rank))` 融合名次后取前 `resultCount`；WEIGHTED_SUM 先按通道做查询内 min-max 归一到 0~1（通道内得分全相同归 1、缺失通道计 0），再按 `denseWeight×向量归一分 + (1−denseWeight)×关键词归一分` 合并排序（tie-break：合并分 → 向量原分 → 关键词原分 → 分片键）后取前 `resultCount`；
- 重排（`rerankEnabled` / `rerankModelId` / `enterRerankCount`）当前仅为知识库配置项（`config.searchParams`），调试请求暂不携带、检索链路接入规划中；
- 问题改写为「半真」：需要 `modelId`（或知识库 `config.modelParams.modelId`），模型缺失/调用失败时自动跳过改写并走原问题，不影响检索；
- `chat/stream` 请求体为 `KbChatBo`：`modelId` / `nearbySliceCount`（拼接邻近文本片数量，默认 5，= 每个命中前后补片数）/ `prompt`（可含 `<Documents>` 变量，缺省用内置模板）/ 检索参数同 retrieve / `messages`（`[{role: user|assistant, content}]`，末条为当前问题）。
  问答链路：检索 → 邻近补全 → Prompt 注入 → 对话模型流式回答；**无状态**（历史由前端携带，不落库、不占运行配额、不计 Agent 用量），对话模型经运行时 `AgentModelResolver` 解析（仅 CHAT 模型可用）。
  推理模型（返回 `reasoning_content`）的思考内容经 AgentScope `ThinkingBlock` 分流为 `REASONING_DELTA`，回答走 `TEXT_DELTA`，前端「知识问答」tab 把思考折叠展示、与回答分开；思考不计入回传给模型的历史（历史只带回答）。

注意事项：
- 手工切片写入与 Worker 索引共用同一条 Embedding→VectorStore 链路；单切片编辑/删除为行级操作，仅默认 PgVectorStore 保证索引同步（ES/Milvus 实例无单条更新 API，编辑后需重新索引文档保持一致）；
- 手工切片的所属文档：新增时 `documentId` 必填（缺失报「请选择所属文档」、跨库报「文档不存在或不属于该知识库」）；切片建立后所属文档不可更改（`PUT` 只接受 `content`）；
- 手工切片的增/改/删均要求**所属文档已进入索引终态**（3-处理完成 / 4-处理失败）：文档处于待处理/解析中/处理中时，Worker 会先删除该文档的全部切片与向量再重建，窗口内的手工改动会被静默覆盖，故服务端直接拒绝（报「所属文档尚未完成索引」）；
- 新增/删除切片同步增减所属文档的 `chunk_count`（编辑不动）；存量偏差不回填，随下一次重新解析整体重算；该列按增量读改写维护，同一文档的并发手工增删理论上可丢一次计数（重索引自愈）。
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

Key 归属创建用户，可绑定**多个已发布（PUBLISHED）Agent**（见迁移 `V35__api_key_user_agent_binding.sql`、
`V36__api_keys_tracking_id_uuid.sql`、`V37__api_keys_key_hash.sql`）。
明文 Key 仅创建时返回一次，形如 `v5ai-<32 位大小写字母与数字>`，不含任何跟踪信息；
库内只存 `key_hash`（明文 SHA-256，运行时唯一索引定位密钥行）与 `secret_hash`（BCrypt，二次校验）。
`tracking_id` 是与明文无关的独立 UUID，只用于展示与日志/审计对账。

```http
GET    /api/admin/api-keys               # 分页：当前用户自己的 Key（含 agentKeys / lastUsedAt）
                                         # name 在同一用户内唯一：重名返回 409「Key 名称已存在，请换一个名称」
POST   /api/admin/api-keys               # {"name":"生产环境","agentKeys":["agentKey1","agentKey2"]}
                                         # → {"id","name","trackingId","apiKey","agentKeys"}，明文仅此一次
PUT    /api/admin/api-keys/{id}          # {"name","agentKeys","enabled"}，agentKeys 为覆盖式重绑
PUT    /api/admin/api-keys/{id}/enabled  # {"enabled":false} 停用
DELETE /api/admin/api-keys/{ids}         # 批量删除（逗号分隔主键，与角色管理一致），删除 Key 及其绑定
```

绑定校验在服务端执行：非 PUBLISHED 的 agentKey 一律拒绝（`400`）。
运行期 `/api/v1/agents/{agentKey}/...` 鉴权结果：`401` = Key 无效/已停用；
`403` = Key 有效但未绑定该 agentKey；`429` = 按 agentKey 的限流/配额超限。
鉴权成功会刷新 `last_used_at`。

## 对话门户（终端用户侧，API Key 鉴权）

独立前端 `v5ai-ui-chat` 只用 API Key（Header `Authorization: Bearer <api-key>`）访问：

```http
GET /api/v1/agents/auth/bootstrap            # Key 信息（名称/跟踪 ID/归属用户）+ 该 Key 可访问且仍已发布的 Agent
                                             # 每个 Agent 含 name/description/avatarUrl/greeting/presetQuestions/webSearchEnabled
GET /api/v1/agents/auth/{agentKey}/avatar   # 代读上传的头像（管理端资源地址受登录态保护，门户拿不到令牌）
```

- `/api/v1/agents/auth/bootstrap` 落在 `/api/v1/agents/*` 前缀下但不带 agentKey：只校验 Key，不校验绑定、不计配额/限流。
- 门户端点统一在 `/api/v1/agents/auth/**` 下：`/auth/bootstrap` 只校验 Key；`/auth/{agentKey}/avatar` 额外校验绑定，且不计入 Agent 配额/限流（429 只作用于 `/api/v1/agents/{agentKey}/**` 运行路径）。
- 会话 id 由前端生成（UUID）并随每次提问提交；历史通过 `GET /api/v1/agents/{agentKey}/chat/conversations/{id}` 读取；会话清单只存在浏览器本地。
- `chat/stream` 请求体新增可选 `webSearch`：`null`=按 Agent 配置（默认）、`false`=本次显式关闭、`true`=请求开启。**只能收窄不能放大**：Agent 未开启联网时怎么传都不会联网。

## 运行时（RAG 注入）

`POST /api/v1/agents/{agentKey}/chat/stream` 会自动：

1. 解析已发布 Agent；
2. 查询绑定的知识库，对用户问题做向量检索（无 EMBEDDING 模型时回退 Hash 嵌入；向量为空回退关键词检索）；
3. 命中时先发出 `RETRIEVAL` 事件（payload 为引用上下文），再把上下文注入 System Prompt；
4. 之后发出 `MODEL_CALL` / `TEXT_DELTA` / `MESSAGE_COMPLETED` / `RUN_COMPLETED`（失败为 `RUN_FAILED`）。

运行时检索默认「向量优先、向量为空回退关键词、简单去重取 top 4」（快速路径，不执行融合、不引入双路检索开销）。当该 Agent 绑定的知识库**全部**显式配置一致（`config.searchParams.fusionStrategy` = `RRF` 或 `WEIGHTED_SUM`，含 `rrfK` / `denseWeight`）时，运行时执行双路融合：候选数 = `max(注入条数×3, 30)`，融合后截断至注入预算（注入条数保持独立默认 4，**不读 `resultCount`**，避免上下文膨胀）；任一库未显式配置或配置不一致时回退快速路径。效果：调试里显式选融合并保存的库，线上即按同一融合口径检索；阈值过滤 / 问题改写 / 重排仍仅作用于调试链路（重排接入规划中）。

## 会话历史与恢复

```http
GET  /api/v1/agents/{agentKey}/chat/conversations/{conversationId}   # 历史消息列表
POST /api/v1/agents/{agentKey}/chat/conversations/{conversationId}/resume
Content-Type: application/json

{ "query": "继续上一个问题" }
```

恢复调用会携带历史消息作为上下文重跑，并继续持久化新消息与 AgentState。
