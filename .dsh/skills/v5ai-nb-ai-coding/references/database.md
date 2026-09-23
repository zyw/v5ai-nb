# 数据库约定

## 表设计公共字段（PostgreSql）
 - id          主键、类型bigserial NOT NULL,
 - status      状态字段，默认值'0' 操作状态（0正常 1异常）,
 - del_flag    删除标志字段，默认值'0',character(1) DEFAULT '0' 删除标志（0代表存在 2代表删除）
 - create_by   创建人字段，
 - created_at  创建时间字段 类型timestamp without time zone,
 - updated_by  更新人字段，
 - updated_at  更新时间字段 类型timestamp without time zone,
 - remark      描述 类型varchar(1000)

## 表设计公共字段（MySql）
 - id          主键、类型bigint NOT NULL AUTO_INCREMENT,
 - status      状态字段，默认值'0' 操作状态（0正常 1异常）char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',,
 - del_flag    char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
 - create_by   创建人字段，
 - created_at  创建时间字段 类型datetime,
 - updated_by  更新人字段，
 - updated_at  更新时间字段 类型datetime,
 - remark      描述 类型varchar(1000)

## 双方言（PostgreSQL / MySQL）约定

业务库由 `V5AI_DB_DIALECT`（默认 `postgresql`）切换，详见 `docs/adr/0012-multi-dialect-database-support.md`。

- 迁移目录按方言分层：`db/migration/common/`（两方言通用）、`db/migration/postgresql/`（V1–V50）、
  `db/migration/mysql/`（V1/V2 基线 + V50 起与 PG 同号各一份）。
  **同一版本号在一个方言下只能出现一次**；新增迁移要么只放 `common/`，要么两个方言目录各放一份同号。
- 运行时 SQL 的方言判定**不读配置项**：mapper 里用 `<when test="_databaseId == 'mysql'">`，
  Java 侧用 `DataBaseHelper.getDataBaseType()`。未识别到方言时 `_databaseId` 为 null，会落到 PG 分支。
- **PG 专有写法（`::` 强转、`ILIKE`、`ON CONFLICT`、`text[]`/`&&`/`@>`/`cardinality`/`unnest`、
  `interval '1 minute'`、`DISTINCT ON`）只能出现在方言分支内**——`MapperSqlDialectLintTest` 会扫全部
  mapper XML 并在违规时失败。能写成两方言通吃的就别分支：`CAST(x AS DATE)`、`LOWER(c) LIKE LOWER(?)`、
  `COALESCE`、`CURRENT_TIMESTAMP`。
- MySQL 侧写迁移/SQL 的注意：`BIGINT AUTO_INCREMENT`、`DATETIME(3)`（默认值须写 `CURRENT_TIMESTAMP(3)`，
  fsp 要与列一致）、`JSON`、`LONGBLOB`、`TINYINT(1)`；注释用内联 `COMMENT`（无 `COMMENT ON`）；
  没有 `ADD COLUMN IF NOT EXISTS`；`ON DUPLICATE KEY UPDATE` 无条件 WHERE，条件更新要用 `IF()`，
  且赋值**从左到右求值**——被当作判断依据的列必须放最后一个。
- 表/列命名避免 MySQL 保留字（现库无冲突）；`utf8mb4` + `utf8mb4_0900_as_cs`（区分大小写，与 PG 一致）。
- schema 对账工具：`docs/db/dump-schema.sql`（PG）与 `docs/db/dump-schema-mysql.sql`（MySQL）输出同一套列。
