# 支持通过配置切换 PostgreSQL / MySQL 8.0

## Context

当前系统硬绑定 PostgreSQL：`application-dev.yml.template` 里 `driverClassName: org.postgresql.Driver` 写死，Flyway 迁移（V1–V49）是 PG 方言，`spring.flyway.locations=classpath:db/migration` 无方言维度，RAG 关键词路的 `text[]`/`jsonb`/数组操作符与 3 处 `ON CONFLICT` upsert 都是 PG 专有。目标是加一个配置项即可把业务库切到 MySQL 8.0，PG 仍是默认且行为不变。

已确认的范围（用户决策）：

- 目标 MySQL 8.0+（可用 JSON 类型、`JSON_TABLE`、函数多值索引）。
- 全功能对齐，RAG 向量检索走独立向量存储实例（Milvus / Elasticsearch / 仍可选的 pgvector 实例）——向量本就与业务库解耦（V30 已 DROP `embedding` 列，`VectorStore`/`KeywordStore` 端口 + `VectorStoreResolver` 已按实例类型分派），不新增 MySQL 向量后端。
- 不需要 PG→MySQL 存量数据迁移，只保证全新空库能在 MySQL 上从零建出等价 schema + 种子数据。

关键既有资产（不要重复造）：

- `common-mybatis` 的 `DataBaseType` / `DataBaseHelper.getDataBaseType()`（按连接元数据自动识别方言，`findInSet` 已带 MySQL 分支）；`dynamic-datasource` 已配置成型（按方言分 `postgresql`/`mysql` 两块，未用 `@DS`）。
- 全部实体已是 `@TableId(type = IdType.AUTO)`，自增语义在 MySQL 天然成立，无需序列处理。
- `VectorStore` / `KeywordStore` 端口、`VectorStoreResolver` 的类型 switch、`JiebaTextTokenizer` 分词与 `PgBm25KeywordSearch` 的 Java 侧 BM25 打分公式（k1=1.2/b=0.75）均可复用。

## 方案总览

切换方式：新增 `V5AI_DB_DIALECT=postgresql|mysql`（默认 `postgresql`），它决定 Flyway 方言目录，同时作为 `spring.datasource.dynamic.primary` 选中同名数据源块；连接信息沿用既有 `V5AI_DATASOURCE_URL`，驱动不设环境变量——`postgresql`/`mysql` 两块各自写死。运行时 SQL 分支用 MyBatis `_databaseId` 自动识别（与 URL 一致，无需人工声明方言）。

迁移目录改为按方言分层（`common/` 放两方言通用迁移，方言专用放各自目录，同一版本号在任一方言下只出现一次）：

```
v5ai-modules/v5ai-platform/src/main/resources/db/migration/
├── common/          # 未来两方言通用的迁移（暂空 + README 说明规则）
├── postgresql/      # 现有 V1–V49 原样搬入（ADR-0008 已验证 locations 递归扫描、移动不改 checksum）
└── mysql/           # V1__baseline_schema.sql + V2__baseline_seed.sql（MySQL 侧独立基线）
```

`spring.flyway.locations: classpath:db/migration/common,classpath:db/migration/${v5ai.db.dialect}`

MySQL 侧用「当前 head 全量基线」而不是重写 49 个历史迁移：没有存量数据要迁，基线直接等价于 head（含 484 条注释的语义，用 MySQL 内联 `COMMENT` 保留），后续版本两边同步递增。

## M1 配置与依赖切换（PG 行为不变）

- **1.** `v5ai-starter/src/main/resources/application.yml`：新增 `v5ai.db.dialect: ${V5AI_DB_DIALECT:postgresql}`，`spring.flyway.locations` 改为上述双目录表达式。
- **2.** `v5ai-starter/src/main/resources/application-dev.yml.template`（第 9–28 行）与 `application.yml.template`（第 381–385 行注释块）：`dynamic.primary: ${v5ai.db.dialect}` + `postgresql`/`mysql` 两块，驱动按块写死（`org.postgresql.Driver` / `com.mysql.cj.jdbc.Driver`），URL 仍取 `${V5AI_DATASOURCE_URL:...}`，每块保留自己方言的默认串（MySQL 块示例：`jdbc:mysql://localhost:3306/v5ai_nb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`）。
- **3.** `v5ai-starter/pom.xml`：新增 `com.mysql:mysql-connector-j`（runtime）、`org.flywaydb:flyway-mysql`（Flyway 10+ 方言模块，缺则 `Unsupported Database: MySQL`），与既有 `flyway-database-postgresql`、`org.postgresql:postgresql` 并列。
- **4.** 迁移脚本搬迁：`db/migration/V*.sql` → `db/migration/postgresql/`；更新 `db/migration/README.md`（两条规矩 + 索引表 + 新增「方言分层与新增迁移该放哪」一节）。
- **5.** `../script/docker/docker-compose-postgresql.yml`：新增 `mysql:8.0` 服务（放 `profiles: [mysql]`，含 healthcheck、数据卷、utf8mb4 参数），`v5ai` 服务补 `V5AI_DB_DIALECT` 透传；`.env.example` 增补该变量与 MySQL 默认值；`docs/deploy/dev.md` 增加 MySQL 部署小节。
- **6.** 测试修正：`v5ai-starter/src/test/java/xin/v5ai/nb/starter/V5aiApplicationContextTest.java:56` 断言 `spring.flyway.locations` 等于 `classpath:db/migration`，需改为新表达式（`FlywayMigrationTest` 自行覆盖 locations，不受影响）。

## M2 MySQL 基线迁移（本方案主要工作量）

- **7.** `db/migration/mysql/V1__baseline_schema.sql`：按 `docs/db/schema.md`（42 张表 = 9 张 `plm_*` + 33 张 `v5ai_*`；ADR-0008 说的 43 张含 V44 已删的 v5ai_agent_model）生成 MySQL 8.0 DDL，类型映射：`BIGSERIAL`→`BIGINT AUTO_INCREMENT`、`TIMESTAMPTZ`→`DATETIME(3)`、`JSONB`→`JSON`、`BYTEA`→`LONGBLOB`、`BOOLEAN`→`TINYINT(1)`、`text[]`→`JSON`、UUID 列→`VARCHAR(36)`（与现状一致）、表/列注释用内联 `COMMENT`。索引、唯一键、默认值一一对应；不含 pgvector 扩展与 vector 列（V30 已删），`keyword_tokens` 建 JSON 列 + 函数多值索引（`CAST(keyword_tokens AS CHAR(64) ARRAY)`，支撑 `JSON_OVERLAPS`/`JSON_CONTAINS`）。
- **8.** `db/migration/mysql/V2__baseline_seed.sql`：按 PG 侧顺序重放种子 DML（不是手工推最终态）：V18 的 `plm_role`/`plm_user(admin)`/`plm_user_role`/`plm_client`/`plm_menu`/`plm_role_menu` → V21/V22（含 `DELETE plm_menu WHERE menu_name='租户管理'`）/V23/V24/V35/V49 的菜单与授权 → V27 的 12 个 `v5ai_model_provider`（MySQL 用 `INSERT ... ON DUPLICATE KEY UPDATE provider_key=provider_key` 替代 `ON CONFLICT DO NOTHING`）。
- **9.** 校对工具：新增 `docs/db/dump-schema-mysql.sql`（`information_schema` 版元数据查询，替代 PG 专有的 `dump-schema.sql` 查询），用于 M2 验收时与 PG head 逐表逐列比对。
- **10.** 字符集/排序规则决策（需确认）：建议全库 `utf8mb4` + `utf8mb4_0900_as_cs`（大小写敏感，与 PG 现网语义一致，避免唯一键/登录名在 MySQL 上变成大小写不敏感而放宽约束）。

## M3 运行时 SQL 方言化（不方言化则 MySQL 上写链路直接报错）

- **11.** 注册 `VendorDatabaseIdProvider`（映射 `PostgreSQL`→`postgresql`、`MySQL`→`mysql`），mapper 用 `_databaseId` 分支；若 MyBatis-Plus 自动装配未透传，则退为显式 `SqlSessionFactory`（`MybatisSqlSessionFactoryBean#setDatabaseIdProvider`）——两者取其一，实现后再定。
- **12.** `KnowledgeChunkMapper.xml`：`metadata` 的 `::jsonb`（写 + `EXCLUDED.metadata`）→ 仅 PG 分支保留强转；`TextArrayTypeHandler` 改为方言感知（PG 走 `createArrayOf("text")`，MySQL 走 JSON 序列化）。
- **13.** 三处 upsert 分支：`V5aiAppUsageMapper.xml`、`KnowledgeChunkMapper.xml`（`ON CONFLICT (vector_id) DO UPDATE`）、`ConversationSummaryMapper.xml`（带 `WHERE` 的条件 upsert，MySQL 用 `ON DUPLICATE KEY UPDATE + IF()`，注意把 `covered_until_message_id = GREATEST(...)` 放在 `summary_text` 赋值之后，保证条件读到旧值，且水位只增不减语义不变）。
- **14.** 其余 PG 专有片段：`KnowledgeTaskMapper.xml` 的 `now() - n * interval '1 minute'` → MySQL `DATE_SUB(NOW(), INTERVAL ? MINUTE)`；`KnowledgeChunkMapper.xml` 的 `ILIKE` → `LOWER(content) LIKE LOWER(#{keyword})`（两方言通吃，无需分支）；`StatsController`、`ApiKeyUsageStatsService` 的 `created_at::date`/`started_at::date` → 可移植的 `CAST(x AS DATE)`。
- **15.** `PgBm25KeywordSearch` 的召回/统计 SQL 方言化（PG `&&`/`@>`/`cardinality`/`unnest` ↔ MySQL `JSON_OVERLAPS`/`JSON_CONTAINS`/`JSON_LENGTH`/`JSON_TABLE`），使 pgvector 实例在 MySQL 业务库上仍可用，并为 M4 铺路。

## M4 关键词检索在 MySQL 上不依赖 ES（可选，建议做）

现状：运行时 `RetrievalContextBuilder` 的 keyword 通道在未配搜索引擎时回退到向量实例自身的关键词能力（Milvus 没有 → 跳过）；调试侧 `KnowledgeRetrievalServiceImpl.keywordHits` 只用向量实例。因此 MySQL + Milvus 组合下关键词检索会缺失。

- **16.** 新增存储实例类型 5 = `MYSQL_FULLTEXT`（category=2 搜索引擎，无连接配置，走应用数据源 + `Bm25KeywordSearch`），`VectorStoreResolver` 补分支；否则该能力需绑定 Elasticsearch 实例才可用。
- **17.** `KnowledgeRetrievalServiceImpl.keywordHits` 补「向量实例不支持 `KeywordStore` 时回退到知识库的 `searchEngineInstanceId`」，与 `RetrievalContextBuilder` 对齐（顺带修掉 Milvus 场景的既有缺口）。
- **18.** 前端 `v5ai-ui` 存储实例类型下拉加该项；`StoreInstanceConfigDialog`/`VectorDimensionService`/`StoreConnectionTester` 相应分支（MySQL 全文无需连通性测试与维度上限）。

## 复用与不变量

- 复用 `DataBaseHelper`/`DataBaseType`、`VectorStore`/`KeywordStore` 端口与 `VectorStoreResolver`、`JiebaTextTokenizer`、BM25 打分公式、`IdType.AUTO`。
- PG 侧 V1–V49 内容一个字节不改（ADR-0008：只增不改、checksum 不能动；文件搬家安全已验证）；`docs/db/schema.md` 与 ADR-0008 作为历史记录保留，新增 `docs/adr/0012-multi-dialect-database-support.md` 记录本次决策（方言目录、基线策略、排序规则、向量仍走外部实例、不做存量数据迁移）。
- 同步更新：`AGENTS.md` §2 技术基线与 §7 数据库与迁移、`docs/deploy/dev.md`、`.dsh/skills/v5ai-nb-ai-coding/references/database.md`（其 MySQL 字段约定已存在，补 `-Dsurefire.failIfNoSpecifiedTests=false`）。
- MySQL 全新库：`docker compose -f docker-compose-mysql.yml up -d --build`（本机当前无 docker CLI，需在有 Docker/MySQL 的环境执行），以 `V5AI_DB_DIALECT=mysql` `V5AI_DATASOURCE_URL=jdbc:mysql://...` 启动 → 断言 42 张表 + 种子数据齐备（admin/admin 可登录、菜单树/角色/客户端/12 个供应商存在）。
- Schema 等价性：分别执行 `docs/db/dump-schema.sql`（PG）与新增的 `docs/db/dump-schema-mysql.sql`（MySQL），比对表名与列名集合、可空性、唯一键；差异需解释到零计划外差异。
- 功能端到端（MySQL）：登录 → 配模型（凭据 AES 加密）→ 建 Agent → 签发 API Key → 建知识库（向量实例绑 Milvus 或 ES）→ 文档索引（worker 写入向量实例，业务行落 MySQL）→ 知识检索/问答（含关键词通道）→ 门户 SSE 对话 → 会话改名/归档/重新生成（覆盖 3 处 upsert 分支）→ 用量统计页（覆盖 `CAST(... AS DATE)`）。
- 可选自动化：`v5ai-starter` 增 Testcontainers（MySQL 8 + PG16）迁移 parity 测试，奢侈地覆盖「两方言 schema 集合一致」，无 Docker 时跳过。

## 风险与不在范围

- 迁移双份维护的 drift 风险 → 用 `common/` 承载通用迁移 + README 规则 + parity 校验兜住。
- MySQL 大小写敏感排序规则若选默认 `_ai_ci`，会放宽唯一键与用户名匹配语义 → 建议 `_0900_as_cs`，此点请在批准时确认。
- MySQL 无 pgvector → 向量必须绑外部实例，`PG_VECTOR` 实例类型保留但仅 PG 可用（部署文档需写明）；`v5ai-code-generator` 目前只产 PG 方言 DDL，本次不覆盖。
- 不在范围：PG→MySQL 存量数据搬迁、MySQL 原生向量类型/ANN 索引、MySQL 5.7 兼容。