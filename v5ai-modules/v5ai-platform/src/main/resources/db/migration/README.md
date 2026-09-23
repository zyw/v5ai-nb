# db/migration —— Flyway 迁移目录

本目录是**唯一权威**的 schema 来源；`docs/db/schema.md` 只是它的快照。

## 三条规矩

**1. 历史只增不改。** Flyway 默认 `validate-on-migrate=true`，每个已应用文件的内容摘要（checksum）都记在
`v5ai_flyway_schema_history` 里。改动任何**已被某个库应用过**的迁移文件一个字节，该库下次启动就会
`Migration checksum mismatch` 直接起不来（本项目只有一个库，且里面是全部存量数据）。

**2. 想读懂「现在长什么样」，不要 replay 这一堆文件。** 直接看 [`docs/db/schema.md`](../../../../../../../docs/db/schema.md)：
那里有迁移头的完整 schema 与每一列的注释。

**3. 同一版本号，在一个方言下只能出现一次。** 目录按方言分层（见下一节），某个库实际能看到的
迁移集合是 `common/` + 它自己的方言目录。因此一个版本要么只在 `common/`（两方言通用、写法必须两边都成立），
要么在 `postgresql/` 与 `mysql/` **各一份同版本号**（方言专用）。两边都有、或只在其中一个方言目录里放
一半（另一方言永远等不到这个号）都是错的——前者 duplicate version，后者两库 schema 从此分叉。

## 目录布局（双方言）

业务库支持 PostgreSQL（默认）与 MySQL 8.0.17+，由 `V5AI_DB_DIALECT` 选择，
`spring.flyway.locations = classpath:db/migration/common,classpath:db/migration/${v5ai.db.dialect}`
（见 `v5ai-starter/src/main/resources/application.yml`）。

| 目录 | 内容 |
|---|---|
| `common/` | 两方言通用的迁移。**目前是空的**（只有本说明），下一个新迁移若写法两边都成立才放这里 |
| `postgresql/` | V1–V50 的 PostgreSQL 迁移（含 pgvector 扩展与 PG 专有 DDL），历史冻结、只增不改 |
| `mysql/` | `V1__baseline_schema.sql` + `V2__baseline_seed.sql`：MySQL 侧从**当前 head 全量基线**起步，不重放 PG 的 49 步历史；V50 起与 PG 同号各一份 |

MySQL 侧的版本号与 PG 侧**互不相干**：MySQL 库的 `v5ai_flyway_schema_history` 里是 `1, 2, 50, 51…`，
PG 库是 `1…49, 50, 51…`。Flyway 只按「本方言可见的集合」校验，缺口（MySQL 没有 V3–V49）不是错误。
新增迁移时从 **V51** 起号（下一个可用号），双方言各写一份或放 `common/`，见规矩 3。
决策取舍与类型映射见 [`docs/adr/0012-multi-dialect-database-support.md`](../../../../../../docs/adr/0012-multi-dialect-database-support.md)。

## 索引（postgresql/ 的 PG 历史）

| 版本 | 文件 | 做了什么 |
|---|---|---|
| V1 | `V1__phase1_schema.sql` | Phase 1 基础：model/provider/agent/version/conversation/message/run/run_event/agent_state/api_key/配额用量/审计等首批表 |
| V2 | `V2__rag_schema.sql` | RAG：knowledge_base/document/chunk/task + pgvector 扩展与 ivfflat 索引 |
| V3 | `V3__provider_type_column.sql` | provider 增加 type 列 |
| V4 | `V4__runtime_columns_to_varchar.sql` | 运行时会话/消息/run 的 UUID 列由 uuid 改 varchar（避免 uuid = character varying 报错） |
| V5 | `V5__mcp_schema.sql` | MCP：server/tool/tool_call 审计 + agent_mcp 绑定 |
| V6 | `V6__skill_schema.sql` | Skill：skill/version/file + agent_skill 绑定 |
| V7 | `V7__platform_enhancements.sql` | 平台增强：tenant（后被 V22 删除）、role、menu、配额用量、model_usage、audit_log |
| V8 | `V8__agent_rename.sql` | Application → Agent 改名（表/列/序列） |
| V9 | `V9__role_management.sql` | 角色管理 |
| V10 | `V10__menu_management.sql` | 菜单管理 |
| V11 | `V11__backfill_admin_role.sql` | 回填 admin 角色 |
| V12 | `V12__workflow_management.sql` | Workflow：workflow/run/node_run |
| V13 | `V13__agent_display_features.sql` | Agent 展示字段（头像/开场白/预设问题）+ 能力开关 |
| V14 | `V14__user_profile_fields.sql` | 用户资料字段 |
| V15 | `V15__user_drop_enabled.sql` | 用户启用位改 status |
| V16 | `V16__role_rename_and_data_scope.sql` | 角色 role_name 与 data_scope |
| V17 | `V17__oper_log_and_login_info.sql` | 操作日志与登录日志表 |
| V18 | `V18__rbac_rewrite_and_client.sql` | RBAC 重写（plm_* 前缀）+ plm_client |
| V19 | `V19__oper_log_login_info_rename_and_client_key.sql` | 日志表更名 plm_* + client_key |
| V20 | `V20__app_quota_usage_created_at.sql` | 配额/用量表补 created_at |
| V21 | `V21__menu_perms_log_and_client.sql` | 菜单权限标识 + 日志/客户端菜单 |
| V22 | `V22__workflow_menu.sql` | 工作流菜单 + 删除孤儿表 v5ai_tenant |
| V23 | `V23__store_instance.sql` | 存储实例 + 资源菜单 |
| V24 | `V24__knowledge_base_extend.sql` | 知识库扩展 |
| V25 | `V25__store_instance_add_description.sql` | 存储实例 description |
| V26 | `V26__model_provider_desc_icon.sql` | 模型供应商描述/图标 |
| V27 | `V27__seed_model_providers.sql` | 种子模型供应商 |
| V28 | `V28__model_config_jsonb_to_text.sql` | 模型 config jsonb → text |
| V29 | `V29__model_endpoint_to_base_url.sql` | 模型 endpoint → base_url |
| V30 | `V30__rag_vector_store_decouple.sql` | RAG 向量存储解耦 |
| V31 | `V31__keyword_tokens_bm25.sql` | 关键词路 jieba + BM25（keyword_tokens） |
| V32 | `V32__agent_rag_call_mode.sql` | Agent RAG 调用方式（rag_call_mode） |
| V34 | `V34__rbac_primary_key_auto_increment.sql` | RBAC 表主键改数据库自增（V33 未使用） |
| V35 | `V35__api_key_user_agent_binding.sql` | API Key 用户与 Agent 绑定 |
| V36 | `V36__api_keys_tracking_id_uuid.sql` | tracking_id 改 UUID |
| V37 | `V37__api_keys_key_hash.sql` | API Key key_hash 与名称唯一 |
| V38 | `V38__portal_conversations_attachments.sql` | 门户会话归属（api_key_id/name/archived_at）+ 消息附件表 |
| V39 | `V39__message_usage_and_supersede.sql` | 助手回答用量与用时落库 + 消息作废标记 superseded_at |
| V40 | `V40__conversation_auto_naming.sql` | 会话名称来源 name_source + 存量会话名回填 |
| V41 | `V41__conversation_summary.sql` | 会话历史摘要表 v5ai_conversation_summary + 修正用量列注释 |
| V42 | `V42__message_reasoning_and_metadata.sql` | 助手消息思考列 reasoning（只回看不回放）+ 保留列 metadata |
| V43 | `V43__schema_comments.sql` | 补齐 schema 注释：221 个列注释 + 29 个表注释（纯 COMMENT ON，无 DDL） |
| V44 | `V44__drop_agent_model.sql` | 删除遗留死表 v5ai_agent_model（0 行、无实体、无写入方） |
| V45 | `V45__message_metadata_citations.sql` | 消息引用快照 metadata 语义落地 |
| V46 | `V46__agent_secondary_model_and_citations.sql` | Agent 次要模型与门户 RAG 引用展示开关 |
| V47 | `V47__knowledge_document_parser_metadata.sql` | 保存文档实际解析引擎与结构化解析诊断 |
| V48 | `V48__model_identity_comments.sql` | 移除模型 provider_id/model_key 联合唯一约束，新增用量 model_id、索引及相关注释 |
| V49 | `V49__menu_full_button_perms.sql` | 菜单树补齐到按钮级：9 个二级菜单补 perms + 66 个按钮权限 + ADMIN/USER 授权 |
| V50 | `postgresql/V50` + `mysql/V50` | 存储实例类型 4 转正为 DB_FULLTEXT（业务库原生 BM25，见 docs/adr/0012）；只改列注释 |

> V1–V41 是历史（冻结于 2026-09-19）；V42 起为后续演进。**V33 从未创建**，属跳号，不是缺失。当前最大迁移版本为 V50（V50 起为双方言同号各一份）。

## 新增一个迁移

1. **取下一个递增号**（当前最大是 V50，新迁移从 V51 起）——号一旦被任何库应用过就只能顺延，不能改名；
2. 命名 `V<n>__snake_case_描述.sql`，与 Flyway 的默认命名规则一致；
3. **先决定放哪**（规矩 3）：写法两方言都成立 → `common/` 一份；否则 `postgresql/` 与 `mysql/` **各写一份同版本号**。
   MySQL 那份要显式写列名（`ALTER TABLE t ADD COLUMN a VARCHAR(64) NULL COMMENT '...'`），
   不要用 PG 的 `ADD COLUMN IF NOT EXISTS`（MySQL 不支持）、`TIMESTAMPTZ`、`BIGSERIAL`、`COMMENT ON`；
4. **写头注释**：说清背景（为什么现在要改）、边界（这个迁移**不做**什么）。V42/V43/V44 是现成的样板；
5. 改现有表一律 `ALTER`，不要回头改历史文件；
6. 有条件就重新生成一次 `docs/db/schema.md`（PG 见 [`docs/db/dump-schema.sql`](../../../../../../../docs/db/dump-schema.sql)，
   MySQL 见 [`docs/db/dump-schema-mysql.sql`](../../../../../../../docs/db/dump-schema-mysql.sql)，
   两份输出列一致，可直接对账）。

## 为什么没有把历史压平

「文件太多」是真实的观感问题，但压平（合并成单个 baseline）必须让现网库要么重建、要么 `repair`，
风险与收益不成比例。取舍与备选方案见 [`docs/adr/0008-migration-history-frozen.md`](../../../../docs/adr/0008-migration-history-frozen.md)。

## 新的数据库会初始化数据的表
| 表 | 数据内容 | 来源迁移 |
| --- | --- | --- |
| `plm_user` | 管理员账号 admin（密码 BCrypt，明文 admin，见 docs/deploy/dev.md 4.3） | V18 |
| `plm_role` | 2 个角色：超级管理员 ADMIN、普通用户 USER | V18 |
| `plm_user_role` | 1 条绑定：admin → ADMIN | V18 |
| `plm_client` | 1 个 PC 客户端（client_id e5cd7e48...，与前端 .env 的 VITE_APP_CLIENT_ID 对应） | V18 |
| `plm_menu` | 完整菜单树 + 按钮级权限（V18 建 16 个初始菜单，V21/V22/V23/V24/V35 增量加菜单，V49 一次补 70 个按钮权限） | V18、V21、V22、V23、V24、V35、V49 |
| `plm_role_menu` | 角色-菜单授权（ADMIN 全量、USER 部分，跟随各次菜单插入） | 同上 |
| `v5ai_model_provider` | 12 个常用模型供应商：openai、anthropic、gemini、dashscope、deepseek、moonshot、zhipu、minimax、tencent、volcengine、siliconflow、ollama（ON CONFLICT DO NOTHING 幂等，只插供应商，不含具体模型 v5ai_model） | V27 |
| `v5ai_flyway_schema_history` | Flyway 自身迁移记录（配置见 application.yml） | 框架自动 |

MySQL 库的种子由 `mysql/V2__baseline_seed.sql` 按同一顺序重放上述 DML，**最终态与上表一致**
（admin 账号、两个角色、一个客户端、同一棵菜单树含按钮权限、12 个供应商）。
