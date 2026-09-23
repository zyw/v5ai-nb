-- ============================================================
-- V31：PG 关键词路升级 —— jieba 分词 + Okapi BM25
-- 方案：OneDev v5ai-nb#2（pgvector 关键词检索升级）
-- 说明：
--   1) chunk 行新增 keyword_tokens（jieba INDEX 模式分词，保留重复词 → 词频在 Java 侧现算）；
--   2) 列上建 GIN 索引，查询用 keyword_tokens && ARRAY[词] 取候选、@> ARRAY[词] 统计 df；
--   3) 存量 chunk 行不删、向量不动，沿用 V30 先例重置文档与索引任务，
--      由 Worker 重跑 PARSE_AND_INDEX 自动重建并填充分词（重建窗口内关键词路对未重建行短暂 miss）；
--   4) df / avgdl 查询时按库集合现算，不做写时计数器（本期不做统计缓存）。
-- ============================================================

ALTER TABLE v5ai_knowledge_chunk ADD COLUMN IF NOT EXISTS keyword_tokens text[];

CREATE INDEX IF NOT EXISTS idx_chunk_keyword_tokens
    ON v5ai_knowledge_chunk USING GIN (keyword_tokens);

-- 存量重建（V30 式）：文档与索引任务重置，Worker 自动重切并填充 keyword_tokens
UPDATE v5ai_knowledge_document
SET status = 0, chunk_count = 0, parse_time = 0, error_message = NULL
WHERE status <> 0;

UPDATE v5ai_knowledge_task
SET status = 'PENDING', attempt_count = 0, error_message = NULL
WHERE task_type = 'PARSE_AND_INDEX' AND status <> 'PENDING';
