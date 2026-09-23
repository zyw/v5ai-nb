-- V50: 存储实例类型 4 从「PG_FULLTEXT（预留、未接线）」转正为「DB_FULLTEXT（业务库原生 BM25）」
--
-- 背景：类型 4 建表时就预留了编号，但 VectorStoreResolver 一直没有实现，配了就返回 null。
-- 业务库支持 MySQL 之后（见 docs/adr/0012），关键词路的候选召回与 BM25 统计都已做方言分支
-- （PG text[] + GIN / MySQL JSON + 多值索引），于是把它接成「不连外部服务、直接用应用业务库
-- 做关键词检索」的后端：没有 pgvector / Elasticsearch 的部署（典型是 MySQL + Milvus 组合）
-- 也能有第二路关键词召回，而不是只剩向量一路。
--
-- 本迁移只改列注释：编号 4 与既有行的取值都不动（历史上不可能有 type=4 的实例被真正用过）。
COMMENT ON COLUMN v5ai_store_instance.type IS
    '类型: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-DB_FULLTEXT（业务库原生 BM25，不连外部服务；历史名 PG_FULLTEXT）';
