-- V50: 存储实例类型 4 从「PG_FULLTEXT（预留、未接线）」转正为「DB_FULLTEXT（业务库原生 BM25）」
--
-- 与 postgresql/V50__store_db_fulltext_type.sql 同一变更的 MySQL 写法：MySQL 没有
-- COMMENT ON，注释随列定义一起 MODIFY。编号 4 与既有取值都不动。
-- 背景见 postgresql 那份与 docs/adr/0012。
ALTER TABLE v5ai_store_instance
    MODIFY COLUMN `type` SMALLINT NOT NULL
    COMMENT '类型: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-DB_FULLTEXT（业务库原生 BM25，不连外部服务；历史名 PG_FULLTEXT）';
