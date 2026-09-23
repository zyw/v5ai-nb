# 业务库支持 PostgreSQL / MySQL 双方言，向量检索仍走独立存储实例

系统此前只支持 PostgreSQL：数据源驱动写死、49 个 Flyway 迁移全是 PG 方言、RAG 关键词路直接用
`text[]` + GIN、三处 upsert 用 `ON CONFLICT`。部署方要求能跑在 MySQL 8 上。本决策确定切换机制、
迁移脚本的组织方式，以及**明确不做什么**。

**Status**: accepted

## 决策

**1. 一个配置项切方言，只决定 Flyway 加载哪套迁移。**
`V5AI_DB_DIALECT=postgresql|mysql`（默认 `postgresql`，未设置时行为与本决策之前完全一致）→
`v5ai.db.dialect` → `spring.flyway.locations = classpath:db/migration/common,classpath:db/migration/${v5ai.db.dialect}`。
连接本身仍由 `V5AI_DATASOURCE_URL` 决定，驱动新增 `V5AI_DATASOURCE_DRIVER`（默认 PG 驱动）；
三者必须配套，不配套时迁移期就会报错，不需要额外的启动校验。
Docker 部署因此**按方言拆成两份 Compose**（`script/docker/docker-compose-postgresql.yml` 装 postgres、
`docker-compose-mysql.yml` 装 mysql，各自都带 redis + v5ai + nginx）：一份文件只装一个库、
把方言三件套的默认值写死在自己这边，部署方换文件即可，不必在 `.env` 里凑齐三项。
不用「一个文件 + compose profile」的做法——那份 profile 里要同时维护两组默认值与两个 `depends_on`，
`.env` 一旦只改一半就是启动失败，而出错的是部署方。
**运行时 SQL 的方言分支不看这个配置**：由连接元数据自动识别（`DataBaseHelper` / 新注册的
`VendorDatabaseIdProvider` → mapper 里的 `_databaseId`），避免「配置说 MySQL、连的却是 PG」时
按错误的方言拼 SQL。

**2. 迁移目录按方言分层，MySQL 侧用「当前 head 全量基线」而不是重译 49 个历史迁移。**
`postgresql/` 装既有的 V1–V49（原样搬入：Flyway locations 递归扫描、移动文件不改 checksum 与版本序，
ADR-0008 已核实过这一点，PG 现网库不受影响）。`mysql/` 只有两个文件：
`V1__baseline_schema.sql`（42 张表 + 全部注释/索引/外键，按 `docs/db/schema.md` 逐列生成）与
`V2__baseline_seed.sql`（按 PG 侧顺序**重放** V18/V21/V22/V23/V24/V27/V35/V49 的种子 DML，只改语法不改语义）。
`common/` 留给未来「两方言都成立」的迁移。规矩：同一版本号在一个方言下只能出现一次
（要么只在 `common/`，要么在两个方言目录各一份）。MySQL 库的历史是 `1, 2, 50, 51…`，
PG 库是 `1…49, 50, 51…`，缺口不是错误。

**3. 向量检索不进 MySQL：绑独立的向量存储实例。**
业务库自 V30 起就不存向量（`embedding` 列已删，向量按 `vector_id` 回链），
`VectorStore` / `KeywordStore` 端口的实现按 `v5ai_store_instance.type` 分派（PG_VECTOR / MILVUS /
ELASTICSEARCH）。MySQL 部署下把知识库的向量实例指向 Milvus 或 Elasticsearch 即可，
`PG_VECTOR` 实例类型保留但只有 PG 可用。MySQL 8.0 没有 pgvector 的等价物
（`VECTOR` + `DISTANCE()` 到 9.x 才有且无 ANN 索引），不做 MySQL 原生向量实现。

**4. 关键词 BM25 保留在业务库，SQL 做方言分支；并把预留的存储实例类型 4 转正。**
分词（jieba）与打分（Okapi BM25，k1=1.2/b=0.75）本来就在 Java 侧，只有「候选召回 + df/avgdl/N 统计」
贴着数据库：PG 用 `text[] && / @> / cardinality / unnest` + GIN，MySQL 用 `JSON` 数组 +
`JSON_OVERLAPS / JSON_CONTAINS / JSON_LENGTH` + 多值函数索引
（`CAST(keyword_tokens AS CHAR(64) ARRAY)`）。因此 **MySQL 下限取 8.0.17**（`JSON_OVERLAPS` 与多值索引都需要它）。
粗排表达式两方言不同形（PG 是数组交集计数，MySQL 是逐词项 `JSON_CONTAINS` 求和），
但两者都只是「命中了多少个查询词项」的候选预排序，最终排名仍由 Java 侧 BM25 决定。

BM25 方言化后它就不再属于「PG 后端」，于是把建表时预留、从未接线的存储实例类型 **4**
（历史名 `PG_FULLTEXT`）转正为 **`DB_FULLTEXT`**：一个「不连外部服务、直接按业务库做 BM25」的
`KeywordStore`（`DbNativeKeywordStore`，`store`/`deleteByDocumentId` 是空操作——分词本来就随业务行落库）。
选 4 而不是新增 5：编号已在校验与前端选项里预留，复用它无需任何数据迁移，V50 只改列注释。
这样 **Milvus 承载向量、又没有 Elasticsearch 的部署仍有第二路召回**（否则关键词这一路只能为空）；
`PG_VECTOR` / `ELASTICSEARCH` 实例继续自带关键词能力，三种后端可选。
顺带对齐一处不一致：调试侧 `KnowledgeRetrievalServiceImpl` 过去只看向量实例有没有关键词能力，
不像运行时 `RetrievalContextBuilder` 那样在配置了搜索引擎时优先用它——现在两边同规则。

**5. 排序规则取 `utf8mb4_0900_as_cs`（区分大小写与重音）。**
MySQL 默认的 `_ai_ci` 会让唯一键与登录名匹配变成大小写不敏感——那是**放宽约束**
（`Admin` 与 `admin` 在 PG 里是两个用户，在 `_ai_ci` 下会撞同一个唯一键）。与现网 PG 语义对齐优先于
「MySQL 用户习惯大小写不敏感」。

## Considered Options

- **一套迁移脚本两方言通用（靠 Flyway 的 `databaseId` 命名后缀，如 `V50__x.mysql.sql`）**：
  只对未来新增迁移成立，既有的 49 个 PG 文件仍然要单独处理，等于两套组织法并存；且
  `_databaseId` 后缀要求 Flyway 先连库再选文件，与 `locations` 一起用容易混乱。否决。
- **MySQL 侧也逐条翻译 V1–V49**：能保持「两边同号同义」，但 V2 的 pgvector、V4/V28 的类型改写、
  V34 的 `setval` 在 MySQL 上要么无意义要么不可能，翻译出来的中间态既跑不通也没人验证；
  而且没有存量数据要搬，逐条重放的唯一产物是「同样一个最终 schema」。否决，改用基线。
- **手写 MySQL 最终态种子（把 49 步的菜单结果直接列出来）**：读起来短，但**没法 review**——
  少一个菜单只能靠人肉对比 70 行菜单树。改成按 PG 顺序重放 DML，逐条对得上原文，
  菜单数不对一眼就能看出来。否决前者。
- **MySQL 也做原生向量（`VECTOR`/应用层算相似度）**：9.x 才有 `VECTOR` 且无 ANN 索引，
  应用层全表算相似度在几万切片上就是每请求全表扫描。否决，向量走外部实例。
- **只支持 MySQL 业务库 + 强制绑定 ES 实例做关键词**：省掉 mapper 的方言分支，但
  「MySQL 部署必须再拉一个 ES」比 PG 部署（pgvector 顺手就有 BM25）明显更重。
  保留 ES 作为可选项，同时做原生分支。否决纯外部方案。
- **用 `V5AI_DB_DIALECT` 驱动所有方言判断（含运行时 SQL）**：一个配置项说了算看似简单，
  但配错就跑出「按 MySQL 拼 SQL 打到 PG」这种静默错误。改成配置只管迁移目录、
  运行时由连接自证。否决。

## Consequences

- **双方言的维护成本落在「新增迁移」上**：每个新迁移要么写成两方言通用放 `common/`，
  要么在 `postgresql/` 与 `mysql/` 各写一份同版本号。漂移风险由 `docs/db/dump-schema.sql`
  与新增的 `docs/db/dump-schema-mysql.sql` 对账兜住（两份查询输出同一套列）。
- **InnoDB 会为每个外键列自动建支撑索引**（PG 不会），所以「只有外键、PG 侧无显式索引」的列
  （如 `v5ai_run_event.run_id`、`v5ai_skill_file.skill_id`）在 MySQL 上会多出一个与约束同名的索引。
  对账时按「外键列的多余索引」解释，不要删（删了 InnoDB 拒绝建外键）。
- **`DATETIME(3)` 不存时区**：PG 的 `TIMESTAMPTZ` 按会话时区呈现，MySQL 侧靠连接串
  `serverTimezone=Asia/Shanghai` 与容器的 `default-time-zone=+08:00` 对齐。
  跨时区部署（非 +08）必须显式改这两处，否则时间读写会偏。
- **MySQL 的 `ON DUPLICATE KEY UPDATE` 按赋值顺序求值**：`v5ai_conversation_summary` 那条
  「水位只增不减」的条件 upsert 在 MySQL 分支里必须把 `covered_until_message_id` 放在**最后一列**赋值，
  前面的 `IF()` 才读得到旧水位；顺序写反的失败是静默的（摘要会被改旧）。
- **`VALUES()` 函数在 MySQL 8.0.20 起标记废弃**：本仓库下限 8.0.17，别名语法（`AS new`）要 8.0.19+，
  两处 upsert 暂用 `VALUES()`。将来抬高下限时应换 `AS new`。
- **`TextArrayTypeHandler` 与 `PgBm25KeywordSearch` 的名字里带 PG，但两者都已方言中立**：
  改名会波及实体注解、mapper XML、`PgVectorStore` 与 `DbNativeKeywordStore` 的装配，收益不值；
  已在两处类注释写明「名字是历史原因」，并把类型 4 的展示名改为「业务库 BM25」而不改存储值。
- **`_databaseId` 只能靠 mapper 分支存在**：MyBatis 未识别到方言（如测试用的 H2）时值为 `null`，
  所有 `<when test="_databaseId == 'mysql'">` 都不成立、落到 `<otherwise>`（PG 写法）。
  因此 mapper lint 测试（`MapperSqlDialectLintTest`）规定 **PG 专有 SQL 只允许出现在方言分支里**，
  并额外钉住「摘要水位列必须是 MySQL 分支的最后一个赋值」——后者写反不报错，是静默的数据错误。
- **`KnowledgeChunk` 补了 `@TableName(autoResultMap = true)`**：否则 `keyword_tokens` 的类型处理器
  不参与结果映射，通用查询（切片详情页、知识问答的邻近切片展开）会把列原始值直接塞给 `String[]`。
  这在 PG 侧同样是隐患（`text[]` → `PgArray`），MySQL 侧是必然报错，故一并修。
- **不在范围**：PG→MySQL 的存量数据搬迁；MySQL 原生向量；MySQL 5.7；
  `v5ai-code-generator` 的 MySQL 方言 DDL 生成（它只产 PG 风格，属于开发期工具）。
