-- ============================================================
-- docs/db/dump-schema-mysql.sql —— 导出 MySQL 侧 schema 全貌（列 + 注释 + 索引）
--
-- 与 dump-schema.sql（PostgreSQL 版）配对使用：两份查询输出同一套列，
-- 把「PG 迁移头重放库」和「MySQL 基线库」各跑一遍即可逐表逐列对账，
-- 用来验证 mysql/V1__baseline_schema.sql 有没有漏表、漏列、漏约束。
-- 权威仍是 db/migration/ 下的迁移文件；这里只是对账工具，不是第二份真相。
--
-- 怎么用：连到目标库后依次执行下面三个查询（先 USE 目标库或用 DATABASE()）。
--
-- 注意：与 PG 版一样刻意转义了 '|' 与换行——它们会撑破 Markdown 表格
-- （v5ai_knowledge_base.delimiter 的默认值就是两个换行）。
-- ============================================================

-- 查询 1：表 + 表注释
SELECT t.TABLE_NAME AS table_name,
       REPLACE(REPLACE(COALESCE(t.TABLE_COMMENT, ''), '|', '&#124;'), '\n', '\\n') AS table_comment
FROM information_schema.TABLES t
WHERE t.TABLE_SCHEMA = DATABASE()
  AND t.TABLE_TYPE = 'BASE TABLE'
  AND t.TABLE_NAME <> 'v5ai_flyway_schema_history'   -- Flyway 记账表，不属于业务 schema
ORDER BY t.TABLE_NAME;

-- 查询 2：列 + 类型 + 约束 + 默认值 + 列注释
SELECT c.TABLE_NAME  AS table_name,
       c.COLUMN_NAME AS column_name,
       CONCAT(c.DATA_TYPE,
              CASE WHEN c.CHARACTER_MAXIMUM_LENGTH IS NOT NULL
                       THEN CONCAT('(', c.CHARACTER_MAXIMUM_LENGTH, ')')
                   WHEN c.DATA_TYPE IN ('decimal', 'float')
                       THEN CONCAT('(', c.NUMERIC_PRECISION, ',', c.NUMERIC_SCALE, ')')
                   WHEN c.DATA_TYPE = 'tinyint' AND c.COLUMN_TYPE LIKE 'tinyint(1)%'
                       THEN '(1)'
                   ELSE '' END) AS data_type,
       IF(c.IS_NULLABLE = 'NO', 'NOT NULL', 'NULL') AS constraint_type,
       -- NULL 默认值写成 <null>，与空字符串默认值区分开
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(c.COLUMN_DEFAULT, '<null>'),
              '|', '&#124;'), '\n', '\\n'), '\r', ''), "''", "'"), '\\', '') AS column_default,
       REPLACE(REPLACE(COALESCE(c.COLUMN_COMMENT, ''), '|', '&#124;'), '\n', '\\n') AS column_comment
FROM information_schema.COLUMNS c
WHERE c.TABLE_SCHEMA = DATABASE()
ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;

-- 查询 3：索引与约束（对账用；PG 版没有这一段，比对时按 PG 的
-- pg_indexes + 约束清单看）
-- 注意：MySQL 的 information_schema.STATISTICS 不记录表达式的真实列序；
-- 用 CAST 表达式建的函数索引会以 NULL 出现在 COLUMN_NAME 中（本仓库只有
-- v5ai_knowledge_chunk.idx_chunk_keyword_tokens 命中这一条），不要误读成漏建。
SELECT s.TABLE_NAME  AS table_name,
       s.INDEX_NAME  AS index_name,
       IF(s.NON_UNIQUE = 0, 'UNIQUE', 'PLAIN') AS uniqueness,
       GROUP_CONCAT(IFNULL(s.COLUMN_NAME, '<expression>') ORDER BY s.SEQ_IN_INDEX SEPARATOR ', ') AS columns,
       IF(s.INDEX_NAME = 'PRIMARY', 'PRIMARY KEY', '') AS extra
FROM information_schema.STATISTICS s
WHERE s.TABLE_SCHEMA = DATABASE()
GROUP BY s.TABLE_NAME, s.INDEX_NAME, s.NON_UNIQUE
ORDER BY s.TABLE_NAME, s.INDEX_NAME;
