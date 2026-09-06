-- ============================================================
-- V30：RAG 向量存储解耦 —— chunk 不再内联 embedding 向量
-- 方案：docs/superpowers/plans/2026-09-06-rag-vector-store-dimension.md
-- 说明：
--   1) 存量 chunk 直接删除（产品决策：不考虑存量迁移，Q13）；
--   2) embedding 列与 ivfflat 索引退役：向量改存存储实例内的
--      per-KB 向量集合（pgvector=独立向量表 / Milvus=collection /
--      ES=index），以业务生成的 vector_id(UUID) 为主键关联；
--   3) 存量文档行保留（content 仍在），其索引任务重置为 PENDING，
--      由 Worker 在新架构下自动重建，无需重新上传；
--   4) vector_id 升级为业务表唯一关联键（原 V24 已建列，无约束）。
-- ============================================================

-- 清空存量切片（向量/内联 embedding 数据一并移除，不做迁移）
DELETE FROM v5ai_knowledge_chunk;

-- ivfflat 索引依赖 embedding 列，必须先删索引再删列
DROP INDEX IF EXISTS idx_v5ai_knowledge_chunk_embedding;

ALTER TABLE v5ai_knowledge_chunk DROP COLUMN IF EXISTS embedding;

-- 存量文档/任务重置为可重建状态（内容仍在 document.content，Worker 重跑即可恢复检索）
UPDATE v5ai_knowledge_document
SET status = 0, chunk_count = 0, parse_time = 0, error_message = NULL
WHERE status <> 0;

UPDATE v5ai_knowledge_task
SET status = 'PENDING', attempt_count = 0, error_message = NULL
WHERE task_type = 'PARSE_AND_INDEX' AND status <> 'PENDING';

-- vector_id 作为业务表唯一关联键（UUID），支撑检索回链与幂等重建
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_chunk_vector_id
    ON v5ai_knowledge_chunk (vector_id);
