# 工单草稿：pgvector 关键词检索升级 —— jieba 分词 + Okapi BM25（方案已确认）

> 用途：手动粘贴到 OneDev `v5ai-nb`（projectId=41）建单，或供自动建单（POST `/~api/issues`）使用。
> 状态：方案已与需求方逐条确认（Q1–Q5 全部采纳），**待编码**。前序设计讨论见会话（grill-with-docs 会话）。
> 关联文档口径：`docs/api/phase2.md` L184 已写「pgvector 为 ILIKE 粗检（命中恒 1.0，BM25 细打分规划中）」——本工单收口该规划。

---

## 背景与现状

- PG 原生关键词路（`PgVectorStore.keywordSearch`，`v5ai-modules/v5ai-rag`）：`KnowledgeChunkMapper.xml` L45-56 对 `v5ai_knowledge_chunk.content` 做 `ILIKE` 粗检，`1.0 AS score` + `ORDER BY id`——无相关度、无排序意义；关键词路即 chunk 业务行本身。
- ES 关键词路已是 `match` + `_score`（text 字段未覆写 similarity，默认即 BM25），不动。
- Milvus 无关键词路；`KeywordStore` 端口签名不变。
- 融合/排序已就位：调试 `KnowledgeRetrievalServiceImpl.keywordHits`（topK = max(resultCount×3,30)）、运行时 `RetrievalContextBuilder.keywordSearch`（topK=4 或融合 30，按搜索实例分组 + 原生兜底），`RetrievalFusion`（RRF/WEIGHTED_SUM/VECTOR/KEYWORD + 去重键 kb:doc:chunk）。
- 迁移基线：`v5ai-modules/v5ai-platform/src/main/resources/db/migration/` 最大 V30 → 新迁移 **V31**。
- 仓库 PG 要求 14+；无 tsvector/zhparser/jieba 先例。

## 目标与语义变化

| 项 | 现状 | 目标 |
| --- | --- | --- |
| PG 关键词得分 | ILIKE 命中恒 1.0、无排序 | jieba 分词 + **Okapi BM25**（k1=1.2, b=0.75）连续相关度；KEYWORD 单路与 WEIGHTED_SUM 获得真实排序语义 |
| 关键词索引数据 | 无（直接扫 content） | chunk 行新增 `keyword_tokens text[]`（jieba INDEX 模式、保留重复词、过滤停用词）+ GIN 索引 |

## 决策记录（Q1–Q5，均已确认）

1. **Q1 范围**：真 BM25 只做 PG 原生关键词路；ES 路 / 外部搜索引擎实例 / Milvus 不动。
2. **Q2 机制**：真 Okapi BM25；分词 token 挂在 `v5ai_knowledge_chunk` 行（text[] + GIN），查询 jieba SEARCH 分词 → GIN 取候选 → Java 按 BM25 计分；df / avgdl 查询时按「查询涉及的库集合」现算（不做写时计数器；超大库后续再加统计缓存，本期不做）。
3. **Q3 存量**：V31 加列建索引 + 沿用 V30 先例重置（文档 `status=0` + 索引任务 `PENDING`）→ Worker 5s 扫描自动重建填充分词；存量 chunk 行不删、向量不动；重建窗口内关键词路对未重建行短暂 miss（向量路 / RRF 不受影响，可接受）。
4. **Q4 归一**：`RetrievalFusion.weightedSum` 融合前对两路得分按通道**查询内 min-max 归一到 0~1**（通道 max==min 时该通道归 1；缺失通道按该通道最小值=0 计）；`combined = denseWeight×vecNorm + (1−denseWeight)×kwNorm`；确定性 tie-break（combined 降序 → vector 分降序 → keyword 分降序 → chunk key 升序）。RRF / VECTOR / KEYWORD / 单路显示分不受影响。
5. **Q5 停用词**：分词后过滤内置轻量停用词表（中文高频虚词 + 基础英文停用词）；自定义词典本期不做。

## 分词依赖（已核实）

- `com.huaban:jieba-analysis:1.0.2`（Maven Central 实测可下载；Apache-2.0；class major 51 → JDK21 直接运行；运行时依赖仅 commons-lang3）。
- API：`JiebaSegmenter.process(text, SegMode.SEARCH|INDEX) → List<SegToken>`（字段 word/startOffset/endOffset）；线程安全可单例（运行时禁改词典）；无内置停用词（应用层做）；首次加载约 1~2s → 启动预热。
- 风险缓释：应用层 `TextTokenizer` 接口留替换缝（上游已停更，备选 master/JitPack 或 HanLP）。

## V31 迁移草案

`v5ai-modules/v5ai-platform/src/main/resources/db/migration/V31__keyword_tokens_bm25.sql`：

```sql
ALTER TABLE v5ai_knowledge_chunk ADD COLUMN keyword_tokens text[];
CREATE INDEX idx_chunk_keyword_tokens ON v5ai_knowledge_chunk USING GIN (keyword_tokens);

-- 存量重建（V30 式）：文档与索引任务重置，Worker 自动重切并填充 keyword_tokens
UPDATE v5ai_knowledge_document SET status = 0, chunk_count = 0, parse_time = 0, error_message = NULL WHERE status <> 0;
UPDATE v5ai_knowledge_task SET status = 'PENDING', attempt_count = 0, error_message = NULL
WHERE task_type = 'PARSE_AND_INDEX' AND status <> 'PENDING';
```

## 改动文件清单

| 层 | 文件 | 动作 |
| --- | --- | --- |
| 依赖 | `v5ai-modules/v5ai-rag/pom.xml` | + com.huaban:jieba-analysis:1.0.2 |
| 迁移 | `v5ai-modules/v5ai-platform/.../db/migration/V31__keyword_tokens_bm25.sql` | 新增 |
| 分词 | `rag/core/tokenizer/TextTokenizer.java`（接口）+ `JiebaTextTokenizer.java`（@Component，单例、停用词、@PostConstruct 预热） | 新增 |
| 检索 | `rag/core/store/PgBm25KeywordSearch.java`（BM25 计分组件）+ `TextArrayTypeHandler.java`（BaseTypeHandler<String[]>，PG text[]） | 新增 |
| 检索 | `rag/core/store/PgVectorStore.java` | keywordSearch 委托新组件；空查询词回退现有 ILIKE |
| Mapper | `KnowledgeChunkMapper.java` + `KnowledgeChunkMapper.xml` | 候选（`keyword_tokens && ARRAY[词]`）/ df（`@> ARRAY[term]` 计数）/ avgdl（`AVG(CARDINALITY(keyword_tokens))`）/ 回填；insert 与 `ON CONFLICT DO UPDATE` 增列 keyword_tokens |
| 写入 | `rag/domain/KnowledgeChunk.java`/`KnowledgeChunkDO.java`、`KnowledgeChunkServiceImpl`（saveBatch 填 token）、`KnowledgeChunkAdminServiceImpl.updateChunk`（改 content 刷新 token） | 填/刷 keyword_tokens（删除路径随行删，无需处理） |
| 融合 | `rag/core/fusion/RetrievalFusion.java` | weightedSum min-max 归一 + 确定性 tie-break |
| 测试 | 新增 TextTokenizer / BM25 计分 / PgBm25KeywordSearch 单测；`RetrievalFusionTest` 与既有加权相关单测按归一语义更新 | 新增/更新 |
| 文档 | `docs/api/phase2.md`（L87 关键词后端口径、L184「BM25 细打分规划中」→ 落地、L185 加权公式注明归一） | 更新 |

明确**不做**：ES 路 / 外部搜索引擎实例 / Milvus / 自定义词典 / `KeywordStore` 端口签名变更。

## 测试与验证

- 单测：分词（停用词过滤 / 小写 / 英文数字整段 / 空输入）；BM25 纯函数（idf 单调、tf 饱和、dl 归一、越界）；PgBm25KeywordSearch（mock mapper：候选+df+avgdl、空词回退 ILIKE、无候选空结果）；RetrievalFusion 归一排序与 tie-break 确定性。
- 端到端：本地 PG 起服务 → Flyway V31 应用 → 文档任务自动重建 → 知识检索 tab：关键词 / RRF / 加权融合输出 BM25 连续分（面板得分不再是 1.0）。
- 构建：JDK21（本机 bellsoft-jdk-21.0.8+12）`mvn -pl v5ai-modules/v5ai-rag -am test -Dmaven.test.skip=false -Dtest=...` + 全量 `mvn compile`。

## 风险

| 风险 | 缓解 |
| --- | --- |
| Central jieba 1.0.2 老旧（2015） | TextTokenizer 接口留替换缝；SEARCH/INDEX+HMM 能力已够用 |
| text[] 类型处理 | 自研 TextArrayTypeHandler + 单测；写入全走受控 XML/实体 |
| 查询时 df/avgdl 现算开销（大库） | GIN 逐词计数；KB 极大再考虑统计缓存（另开工单） |
| 重建窗口关键词路降级 | 沿用 V30 先例；向量路/RRF 不受影响 |
| ILIKE 子串命中语义消失 | jieba 词典 + HMM 覆盖常见写法；文档声明行为变化 |

## 验收标准

1. `keyword_tokens` 列随 Worker 重建与手工切片增改自动维护；删除随行清理。
2. `KEYWORD` 单路 / `WEIGHTED_SUM` 返回按 BM25 相关度排序的连续分；RRF 排序与融合语义不受影响。
3. 空查询词（全停用词）回退原 ILIKE 行为，不返回空错。
4. V31 在存量库应用后文档任务自动进入重建并完成；新库建表即含新列。
5. 单测全绿 + 全量编译通过；phase2.md 口径同步。
