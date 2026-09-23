# common/ —— 两方言通用迁移

本目录的迁移会同时被 PostgreSQL 与 MySQL 两个库加载
（`spring.flyway.locations = classpath:db/migration/common,classpath:db/migration/${v5ai.db.dialect}`）。

因此这里只放**写法在两种方言下都成立**的迁移：

- 可以：`CREATE TABLE x (id BIGINT ...)` 之外——注意自增、类型名、注释语法两边都不同，
  所以「纯建表」几乎不可能通用；真正容易通用的是 `INSERT` 种子（不含方言函数时）、
  显式列出列名的 `ALTER TABLE ... ADD COLUMN a VARCHAR(64) NULL`（MySQL 8 与 PG 同形）、
  以及不依赖函数的数据更新。
- 不可以：`BIGSERIAL` / `TIMESTAMPTZ` / `JSONB` / `ADD COLUMN IF NOT EXISTS`（MySQL 无此语法）/
  `COMMENT ON`（MySQL 用内联 `COMMENT`）/ `ON CONFLICT` / `ILIKE` / `::` 强转 / 部分索引 /
  数组类型 / `DISTINCT ON`。

一个版本号要么只在 `common/`，要么在 `postgresql/` 与 `mysql/` 各一份同版本号——
不能既在 `common/` 又在某个方言目录（Flyway duplicate version），
也不能只在一个方言目录放一半（两库 schema 从此分叉）。

规矩的全文与目录布局见上一级的 [`../README.md`](../README.md)。目前本目录为空（下一个新迁移若是通用写法才放这里）。
