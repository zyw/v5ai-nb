# RAG 向量存储解耦与嵌入维度动态化 — 实现方案

日期：2026-09-06 · 状态：阶段一已完成（存储解耦），阶段二后端核心已完成（config 声明/按 KB 路由/维度检查 API/权威校验），UI 已接线 · 前置：AGENTS.md §13（方案确认后再编码）

## 1. 背景与问题

现状（经代码核查确认，全部 file:line 见探查记录）：

1. **向量内联在业务表**：pgvector 路径把向量写入 `v5ai_knowledge_chunk.embedding vector(1536)`（`V2__rag_schema.sql:34`，ivfflat 索引 :39-40），检索靠 SQL `1-(embedding<=>q)`（`KnowledgeChunkMapper.xml:26-36`），维度不匹配时由 PG 直接报错、无应用层校验。
2. **维度写死 1536 且无契约**：唯一运行常量 `OpenAiCompatibleEmbeddingClient.java:26 DIMENSIONS=1536`；`dimensions()` 无任何调用者；KB 快照 `dimension_of_vector_model`（V24）无消费方；请求体不传 `dimensions` 参数。
3. **展示的配置与运行时脱节**：KB 已有 `embedding_model_id`/`vector_store_instance_id`/`dimension_of_vector_model`（V24），UI 必填并展示，但 Worker 与检索对所有 KB 用**同一个全局 EmbeddingClient**（首个启用的 EMBEDDING 模型），KB 字段零消费。
4. **存储实例管理面完整、运行时半接线**：`v5ai_store_instance`（V23）+ 全套 CRUD/UI/菜单/权限已存在；但 PG_VECTOR(type=1) 实例 config 不参与运行时（`VectorStoreResolver.java:75-77` 恒返主数据源单例），`vector_id` 列已建（V24）但**全链路不赋值**；Milvus/ES 动态取首批 chunk 维度，与 PG 行为不一致。
5. 附带：`updateChunk` 的向量写路径无 `::vector` 转换（42804 风险）；`vector_id` 无唯一约束；docs/api/phase2.md 迁移号（V25/V26）与实际（V24）不符；AGENTS.md 模块描述过时（无 `v5ai-infrastructure`，代码在 `v5ai-modules/`）。

## 2. 目标形态

- 向量与业务表解耦：切片行只保留 `vector_id`（业务生成的稳定 UUID）作为指针，向量承载在**知识库绑定的存储实例**内。
- 存储实例内按**知识库一个向量集合**承载（pgvector=一张按 KB 嵌入维度建的表，Milvus=collection，ES=index），集合内以 `vector_id` 为主键。
- 嵌入维度按 KB 冻结：来源 = 所绑嵌入模型 config JSON 的声明（事实源），落 KB 快照 `dimension_of_vector_model`；维度变更 = 重建该 KB 索引。
- 运行时按 KB 的 `embedding_model_id` 路由 embedding 客户端与维度；检索 = 实例 ANN 召回 `(vector_id, score)` → 回业务表批量取切片内容。
- 新增「模型 × 存储实例」维度上限查询接口（KB 表单实时调用 + 后端保存权威校验）。
- 存量 chunk 数据不迁移：直接清空 + 删内联列（用户拍板）。

## 3. 收敛决策记录（grill 结论）

| # | 决策 | 结论 |
|---|---|---|
| Q1 | 目标粒度 | 先出本方案文档 → 确认后分阶段落地：① 存储解耦；② 维度动态化 |
| Q2 | 维度语义 | 按模型能力声明：固定维度模型 = 输出即实际，库上限只做兼容判定；可调维度模型（支持 `dimensions` 参数）才谈"可选上限" |
| Q3 | 维度挂载/变更 | KB 级冻结快照（`dimension_of_vector_model`，已有列）；变更 = 全量重建索引（复用 delete→re-embed 流水线） |
| Q4/Q5 | 业务库 pgvector | **不是默认存储实例**；PG_VECTOR 保留为合法实例类型、显式化；移除 `instanceId=null` 隐式兜底（`VectorStoreResolver:36-38`） |
| Q6 | 库上限载体 | 引擎类型级默认值 + 实例 config 可覆盖 |
| Q7 | 模型维度事实源 | 模型管理配置（config JSON 的 `embeddingDimension`），**不提升为模型表列**；运行时按 KB `embedding_model_id` 路由（纳入范围） |
| Q8 | vector_id 契约 | 业务侧生成稳定 UUID；chunk 行落库并建唯一索引；三种 store 均以 UUID 为主键；检索 = store ANN → `WHERE vector_id IN (...)` 回链 |
| Q9 | 集合承载 | 每 KB 一个集合；`VectorStore` 端口增加按 KB 确保集合存在的能力 |
| Q10/Q13 | 存量数据 | 不迁移、直接删除：`v5ai_knowledge_chunk` 存量数据可清空 + drop `embedding` 列与 ivfflat 索引 |
| Q11 | 维度接口出参 | `effectiveMaxDimension`（可用上限）/ `modelMaxDimension` / `storeMaxDimension` |
| Q12 | config 声明 | `embeddingDimension`（赋义：实际/默认输出维度）+ 新增 `dimensionAdjustable`/`maxDimension`；保存/测试连接时探测一次回填并告警不一致；所有嵌入调用点（索引/检索/手动重嵌）按 KB 冻结维度执行 |
| Q14 | 兼容性判定 | 出参追加 `dimensionAdjustable`，UI 自行判断；后端保存权威校验 |

## 4. 涉及改动（分两阶段落地）

### 阶段一：存储解耦（vector_id + 每 KB 集合承载）

**数据迁移 V30**（新增，不改历史迁移）：
- `DELETE FROM v5ai_knowledge_chunk;`（存量直接清空，用户拍板）；
- `DROP INDEX idx_v5ai_knowledge_chunk_embedding;` + `ALTER TABLE v5ai_knowledge_chunk DROP COLUMN embedding;`（内联列退役，`updateChunk` 的 42804 风险随列消失而消失）；
- `CREATE UNIQUE INDEX uk_knowledge_chunk_vector_id ON v5ai_knowledge_chunk(vector_id);`（激活已有列，幂等重建前提）。
- 文档/任务表不动（存量文档在重建索引后重新可用；如需一并重置状态另说）。

**向量承载（rag 模块，路径 `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/store/`）**：
- `VectorStore.java`：`VectorChunk` 增加 `String vectorId`；`RetrievalHit` 增加 `vectorId` 并去掉对业务表内联列的假设；新增 `ensureCollection(Long knowledgeBaseId, int dimension)`（幂等建集合/建表+索引）与集合删除能力（随 KB 删除调用）。
- `PgVectorStore.java`：从"chunk 表内联列"改为**每 KB 一张独立向量表**（如表名 `v5ai_vec_{kbId}`，`vector(dim)`，动态 DDL），主键 = `vector_id`；`store`/`deleteByDocumentId`/`search` 全链路改写。
  - ⚠️ 索引自适应（2026-09-06 缺陷修复）：**pgvector 索引有维度上限——ivfflat 恒为 2000；hnsw 依部署版本而定（0.5.x 仍 2000，新版才放开至更高维）**。`ensureCollection` 策略：≤2000 建 ivfflat、>2000 尝试建 HNSW；**索引创建失败降级为无索引精确检索并告警，不阻断写入**（`ORDER BY embedding <=> ?` 全表扫描结果仍正确）。若需 ANN 加速，须升级 pgvector（hnsw 支持该维度）后手动补索引 `CREATE INDEX ... USING hnsw (embedding vector_cosine_ops)`，或改用支持高维的向量库。
- `MilvusVectorStore.java` / `ElasticsearchVectorStore.java`：对齐契约——主键改用业务 `vector_id`（Milvus 关 autoID、ES `_id`=vector_id），集合按 KB + 冻结维度建；行为不再"按首批 chunk 动态取维度"。
- `VectorStoreResolver.java`：**移除 null→pgVectorStore 兜底**（:36-38 改抛错或返回空，由调用方报"未绑定存储实例"）；PG_VECTOR 实例解析仍走主数据源（config 参与外部连接列为后续，见 §8 待确认）。

**chunk 读写链路（rag 模块）**：
- `KnowledgeChunkMapper.xml`：`batchSaveChunks` 删除 embedding 列写入；余弦检索 SQL（:26-36）删除，替换为 `selectByVectorIds`（`WHERE vector_id IN` 批量回链，保留原有列）；ILIKE 关键词检索保留。
- Worker `KnowledgeIndexingService.processTask`：嵌入后生成 UUID → 写实例集合（`ensureCollection` 用 KB 冻结维度，KB 未绑实例 → 任务 FAILED 并给出明确 error_message）；chunk 行只落 `vector_id`。
- `KnowledgeChunkAdminServiceImpl` addChunk/updateChunk/删除：手动切片改/增/删同步维护集合向量与 `vector_id`（更新路径不再有 embedding 字符串 set）。

**检索（rag 模块）**：
- `RetrievalContextBuilder` / `KnowledgeRetrievalServiceImpl`：改为 ① 按 KB 模型嵌入查询 → ② `vectorStore.search` 取 `(vector_id, score)` → ③ `selectByVectorIds` 回业务表取切片内容并回填标题（原 Java 层回填逻辑保留）。
- 调试检索与运行时检索共用同一链路。

### 阶段二：维度动态化 + 按 KB 路由模型（✅ 已实现，2026-09-06）

- **模型 config 声明**：`ModelExtConfigAttrs`（v5ai-common-agentscope）`embeddingDimension` 赋义 + 新增 `dimensionAdjustable`/`maxDimension`（仍存 config JSON，不加模型表列）；ModelsView EMBEDDING 表单新增「维度可调」开关与「可调上限」输入（config 读写已接线）。
- **维度能力服务**（新增 `rag/core/VectorDimensionService`）：按「模型 × 存储实例」计算 `modelMaxDimension`/`storeMaxDimension`/`effectiveMaxDimension`/`dimensionAdjustable`；引擎类型默认上限（pgvector 2000 / Milvus 32768 / ES 2048）+ 实例 config `maxDimension` 键覆盖（`VectorConfigDO` 新增字段，JSON 键名 `maxDimension`）；模型未声明维度时经 `EmbeddingClient.probeDimension` 在线探测兜底。
- **维度探测**：`EmbeddingClient.probeDimension(modelId)` 调一次 `/embeddings`（最小输入）取实际输出维度；由维度能力服务在「模型未声明维度」时懒探测。实现偏差：未挂模型保存/「测试连接」钩子（后续可选增强，不阻塞主流程，探测失败仅不可判定）。
- **按 KB 路由 embedding**：`EmbeddingClient` 接口改为 `embed(text, embeddingModelId, frozenDimension)`；`OpenAiCompatibleEmbeddingClient` 按 KB 绑定模型解析（`embeddingModelId=null` 时回退首个启用 EMBEDDING 模型），`dimensionAdjustable` 模型请求体带 `dimensions=frozenDimension`，固定维度模型不带该参数；无可用模型回退 `HashEmbedding`（维度 = 冻结值或默认 1536）。
- Worker、检索（运行时 + 调试）、手动重嵌三个调用点统一按 KB 的 `embeddingModelId` 与 `dimensionOfVectorModel` 调用。
- **检索分组**：`RetrievalContextBuilder` 向量检索按「向量存储实例 + 嵌入模型 + 冻结维度」复合分组，组内共用一次查询向量（维度一致才可同集合检索）。
- **库上限与类型默认**：类型级默认上限表（pgvector 2000 / Milvus 32768 / ES 2048，见 §7）；实例 config `maxDimension` 键可覆盖。
- **维度查询接口**：`GET /api/admin/knowledge-bases/dimension-check?embeddingModelId=&vectorStoreInstanceId=`（见 §6）。
- **KB 保存校验**：`validKnowledgeBaseBeforeSave` 权威校验——冻结维度 ≤ `min(model, store)`；固定维度模型冻结值须与模型输出维度一致（否则检索维度不匹配）；绑定存储实例必填（阶段一已生效）。
- **UI**：`KnowledgeBasesView` 嵌入模型/向量库切换实时调 dimension-check 回填维度并提示可用上限；`client.ts` 新增 `dimensionCheck`。

### 记账死角（顺手修复，低优先，可分批）
- StoreInstance 类型魔法 int → 常量/枚举；type=4 PG_FULLTEXT 保存时拒绝或标注"未实现"；
- 删除存储实例前校验 KB 引用（现悬空引用风险，`StoreInstanceServiceImpl.java:175-187`）；
- docs/api/phase2.md 迁移号漂移（V25/V26 → V24）与 chunk 扩展描述更新。

## 5. 前端改动（v5ai-ui）

- `KnowledgeBasesView.vue`：删除从实例 config `dimension` 键自动填充的逻辑（:126-140，该键不存在）；改为调维度查询接口实时回填「嵌入维度」并展示 `effectiveMaxDimension`；模型/实例切换即重新查询；不可调模型超限时阻止保存并提示。
- `ModelsView.vue`：EMBEDDING 类型弹窗「嵌入维度」输入框（:1011-1019）保留并接线（写 config JSON）；新增「维度可调」开关与「可调上限」输入（可调时）。
- `StoreInstancesView.vue`：按类型展示引擎默认上限，可覆盖（`max_dimension`）。
- `client.ts`：新增维度查询 API 类型与调用。

## 6. API 变更

**新增「维度检查」组合端点**（放 knowledge-bases 控制器，领域语义）：
```
GET /api/admin/knowledge-bases/dimension-check?embeddingModelId={id}&vectorStoreInstanceId={id}
```
入参：`embeddingModelId` + `vectorStoreInstanceId`。
出参：
```json
{
  "modelMaxDimension": 1024,       // 模型 config 声明（固定输出或可调上限）
  "storeMaxDimension": 2000,       // 类型默认值（实例可覆盖后取实例值）
  "effectiveMaxDimension": 1024,   // min(model, store)
  "dimensionAdjustable": false     // 模型是否支持 dimensions 参数降维
}
```
- UI 语义：`dimensionAdjustable=true` 时 `effectiveMaxDimension` 为可选上限；`false` 且 `effective < modelMax` → 组合不成立，阻止绑定。
- 后端 KB 保存权威校验同口径（不含 UI，仅服务端）。
- 无破坏性变更；`VectorStore` 端口方法签名变更属模块内接口演进（实现同步改）。

## 7. 引擎维度上限（类型默认，实例可覆盖）

| 引擎 | 默认上限 | 依据 | 备注 |
|---|---|---|---|
| pgvector | 2000（部署 0.5+ 可覆盖至 16000） | ≤0.4.x 上限 2000；0.5.0+ 提升至 16000 | 与部署版本绑定，保守取 2000，实例级覆盖 |
| Milvus | 32768 | float 向量官方上限 | 远高于常见模型，覆盖即可 |
| Elasticsearch | **2048** | dense_vector 当前硬上限（1024→2048，社区要求放宽中） | ⚠️ 用户初拟 4096 超出硬上限，会运行时失败，已改为 2048 |

## 8. 待确认点（结论）

1. ✅ **PG_VECTOR 本期走主数据源**（2026-09-06 确认）：v1 中 PG_VECTOR 实例仍解析到主数据源，仅承载形态改为每 KB 独立向量表；config（host/port…）参与运行时、支持外部 pgvector 需每实例 DataSource 基建，列为后续。
2. ✅ **存量处置**（2026-09-06 确认）：不考虑存量迁移，`v5ai_knowledge_chunk` 数据直接清空；存量 document 行保留、索引任务重置为 PENDING 可重建（V30 迁移实现）；`embedding` 列与 ivfflat 索引删除。
3. ✅ **按阶段拆分落地**（2026-09-06 确认）：阶段一（存储解耦）先行，阶段二（维度动态化）随后，各自可验证。
4. ✅ **移除隐式兜底**（2026-09-06 确认）：`VectorStoreResolver` 移除 `instanceId=null → 业务库 pgvector`，KB 必须显式绑定存储实例（KB 保存校验已在 `validKnowledgeBaseBeforeSave` 强制实例非空）。

## 9. 风险

- **动态 DDL（每 KB 建表）**：pgvector 每 KB 建表 + HNSW 索引，KB 多时表数膨胀；表名白名单/参数化防注入；建表失败任务 FAILED 可重试（幂等 `CREATE TABLE IF NOT EXISTS`）。
- **Milvus/ES 主键改造**：现存集合主键语义变化，需重建已索引数据；本期无存量，直接以新语义建集合。
- **检索链路重构回归面大**：调试检索 + 运行时检索共用新链路，需覆盖 PG/Milvus/ES 三种后端的召回正确性与 score 回填。
- **维度探测依赖模型连通**：保存时探测失败不阻塞，但 KB 建库时以冻结维度为准，模型不可用会导致索引失败（沿用现有 FAILED/重试）。

## 10. 测试与验证

- 后端单测：维度检查接口（固定/可调/超限三态）、KB 保存校验、`ensureCollection` 幂等、每 KB 集合维度隔离。
- 集成（starter 测试迁移）：V30 迁移可执行；chunk 清空 + 列删除后 PG 重建索引 → 检索命中。
- 最小运行检查：一个 `KnowledgeChunkMapper.selectByVectorIds` 直查 + `VectorStore.search` 回链正确性的 JUnit 断言。
- 前端：KB 表单模型/实例切换时维度回填正确、坏组合被阻止。
- 端到端：建 KB（绑实例+模型）→ 传文档 → 检索命中 → 改维度 → 重建索引 → 命中恢复。
