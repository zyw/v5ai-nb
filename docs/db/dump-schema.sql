-- ============================================================
-- docs/db/dump-schema.sql —— 导出当前 schema 全貌（列 + 注释）
--
-- 用途：重新生成 docs/db/schema.md。schema.md 是快照，新增迁移后需要重跑一次。
--
-- 怎么用（无需装 psql / pg_dump，任何能跑 SQL 的客户端都行）：
--   1) 把 db/migration 下的迁移全部应用到一个库。两种做法：
--      · 正常路径：启动应用（Spring Boot 的 Flyway 会自动 migrate）；
--      · 或建一个一次性库再手工重放，验证「从零能不能建出同样的 schema」。
--   2) 在这个库上依次执行下面的查询 1 与查询 2；
--   3) 把结果按 schema.md 的格式渲染（每表一节：表注释 + 列清单）。
--
-- 注意：查询里刻意把 '|' 与换行做了转义。
--   · '|' 会撑破 Markdown 表格（v5ai_knowledge_base.delimiter 的默认值就是 ' | '）；
--   · 换行会让一行拆成两行（同一列的默认值在库里是两个换行符）；
--   不转义就会得到一张错位的表——这是踩过的坑。
-- ============================================================

-- 查询 1：表 + 表注释
SELECT c.relname AS table_name,
       replace(replace(coalesce(obj_description(c.oid), ''), '|', '&#124;'), chr(10), '\n') AS table_comment
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
WHERE c.relkind = 'r'
  AND c.relname <> 'v5ai_flyway_schema_history'   -- Flyway 记账表，不属于业务 schema
ORDER BY c.relname;

-- 查询 2：列 + 类型 + 约束 + 默认值 + 列注释
SELECT c.relname AS table_name,
       a.attname AS column_name,
       format_type(a.atttypid, a.atttypmod) AS data_type,
       CASE WHEN a.attnotnull THEN 'NOT NULL' ELSE '' END AS nullable,
       replace(replace(coalesce(pg_get_expr(ad.adbin, ad.adrelid), ''), '|', '&#124;'), chr(10), '\n') AS default_value,
       replace(replace(coalesce(col_description(c.oid, a.attnum), ''), '|', '&#124;'), chr(10), '\n') AS column_comment
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
LEFT JOIN pg_attrdef ad ON ad.adrelid = c.oid AND ad.adnum = a.attnum
WHERE c.relkind = 'r'
  AND c.relname <> 'v5ai_flyway_schema_history'
ORDER BY c.relname, a.attnum;
