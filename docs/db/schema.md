# v5ai_nb 当前 schema（迁移头）

> **这是什么**：`db/migration/` 里**全部迁移应用之后**的 schema 全貌——即下一个新环境从零建库会得到的东西，
> 含每张表的表注释与每一列的注释。它**不是**某个已部署环境的实时快照。
>
> **怎么生成的**：把 V1–V44 完整重放到临时库 `v5ai_nb_verify`（Flyway `migrate`，43 个迁移全部成功），
> 再用 [`dump-schema.sql`](./dump-schema.sql) 导出。生成时间：**2026-09-19**。
> V45 只更新 `v5ai_message.metadata` 的**列注释**（无 DDL 变化），该行已按 V45 迁移文本同步。
> V46（`v5ai_agent` 新增 `secondary_model_id`、`show_citations` 两列）**未走整体重放**，两条列行按迁移文本手工追加
> （列名/类型/约束/默认值/注释逐字取自 V46，新增列在 PG 里排在 `attnum` 末位，故追加在表尾）。
> V47（文档解析引擎与结构化诊断列）和 V48（模型身份、用量 `model_id` 及相关注释）也未走整体重放，
> 相关列、索引与注释已按迁移文本同步到本快照。
> **下次出现真实 DDL 变更时应按上面的流程整体重新生成一次**，以消除手工同步的残留风险。
>
> **权威性**：权威永远是 `db/migration/` 里的迁移文件本身。本文件是**快照**，新增迁移后不重新生成就会滞后；
> 它的价值是「不用 replay 四十多个历史文件就能读懂现在长什么样」，不是取代迁移。
>
> **不含** `v5ai_flyway_schema_history`（Flyway 自己的记账表，不属于业务 schema）。

## 目录

- [`plm_client`](#plm-client) — 16 列
- [`plm_login_info`](#plm-login-info) — 11 列
- [`plm_menu`](#plm-menu) — 21 列
- [`plm_oper_log`](#plm-oper-log) — 21 列
- [`plm_resource`](#plm-resource) — 12 列
- [`plm_role`](#plm-role) — 14 列
- [`plm_role_menu`](#plm-role-menu) — 2 列
- [`plm_user`](#plm-user) — 18 列
- [`plm_user_role`](#plm-user-role) — 2 列
- [`v5ai_agent`](#v5ai-agent) — 21 列
- [`v5ai_agent_knowledge`](#v5ai-agent-knowledge) — 3 列
- [`v5ai_agent_mcp`](#v5ai-agent-mcp) — 3 列
- [`v5ai_agent_skill`](#v5ai-agent-skill) — 3 列
- [`v5ai_agent_state`](#v5ai-agent-state) — 3 列
- [`v5ai_agent_version`](#v5ai-agent-version) — 6 列
- [`v5ai_api_keys`](#v5ai-api-keys) — 10 列
- [`v5ai_api_keys_agent`](#v5ai-api-keys-agent) — 4 列
- [`v5ai_app_quota`](#v5ai-app-quota) — 6 列
- [`v5ai_app_usage`](#v5ai-app-usage) — 6 列
- [`v5ai_conversation`](#v5ai-conversation) — 9 列
- [`v5ai_conversation_summary`](#v5ai-conversation-summary) — 7 列
- [`v5ai_knowledge_base`](#v5ai-knowledge-base) — 17 列
- [`v5ai_knowledge_chunk`](#v5ai-knowledge-chunk) — 14 列
- [`v5ai_knowledge_document`](#v5ai-knowledge-document) — 18 列
- [`v5ai_knowledge_task`](#v5ai-knowledge-task) — 10 列
- [`v5ai_mcp_server`](#v5ai-mcp-server) — 14 列
- [`v5ai_mcp_tool`](#v5ai-mcp-tool) — 10 列
- [`v5ai_mcp_tool_call`](#v5ai-mcp-tool-call) — 11 列
- [`v5ai_message`](#v5ai-message) — 12 列
- [`v5ai_message_attachment`](#v5ai-message-attachment) — 6 列
- [`v5ai_model`](#v5ai-model) — 16 列
- [`v5ai_model_provider`](#v5ai-model-provider) — 8 列
- [`v5ai_model_usage`](#v5ai-model-usage) — 10 列
- [`v5ai_run`](#v5ai-run) — 7 列
- [`v5ai_run_event`](#v5ai-run-event) — 5 列
- [`v5ai_skill`](#v5ai-skill) — 7 列
- [`v5ai_skill_file`](#v5ai-skill-file) — 6 列
- [`v5ai_skill_version`](#v5ai-skill-version) — 8 列
- [`v5ai_store_instance`](#v5ai-store-instance) — 10 列
- [`v5ai_workflow`](#v5ai-workflow) — 11 列
- [`v5ai_workflow_node_run`](#v5ai-workflow-node-run) — 11 列
- [`v5ai_workflow_run`](#v5ai-workflow-run) — 10 列

## plm_client

> 系统授权表

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('plm_client_id_seq'::regclass) | 主键 |
| `client_id` | character varying(64) | NULL | ''::character varying | 客户端id |
| `client_key` | character varying(32) | NULL | ''::character varying | 客户端key |
| `client_secret` | character varying(255) | NULL | ''::character varying | 客户端秘钥 |
| `grant_type` | character varying(255) | NULL | ''::character varying | 授权类型 |
| `device_type` | character varying(32) | NULL | ''::character varying | 设备类型 |
| `access_path` | character varying(2000) | NULL | ''::character varying | 允许访问路径 |
| `ip_whitelist` | character varying(1000) | NULL | ''::character varying | IP白名单 |
| `active_timeout` | integer | NULL | 1800 | token活跃超时时间 |
| `timeout` | integer | NULL | 604800 | token固定超时 |
| `status` | character(1) | NULL | '0'::bpchar | 状态（0正常 1停用） |
| `del_flag` | character(1) | NULL | '0'::bpchar | 删除标志（0代表存在 1代表删除） |
| `created_by` | bigint | NULL | — | 创建者 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_by` | bigint | NULL | — | 更新者 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |

## plm_login_info

> 系统访问记录

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_login_info_id_seq'::regclass) | 访问ID |
| `user_name` | character varying(50) | NULL | ''::character varying | 用户账号 |
| `device_type` | character varying(32) | NULL | ''::character varying | 设备类型 |
| `ipaddr` | character varying(128) | NULL | ''::character varying | 登录IP地址 |
| `login_location` | character varying(255) | NULL | ''::character varying | 登录地点 |
| `browser` | character varying(50) | NULL | ''::character varying | 浏览器类型 |
| `os` | character varying(50) | NULL | ''::character varying | 操作系统 |
| `status` | character varying(30) | NULL | '0'::character varying | 登录状态（0正常 1异常） |
| `msg` | character varying(255) | NULL | ''::character varying | 提示消息 |
| `login_time` | timestamp with time zone | NULL | — | 访问时间 |
| `client_key` | character varying(32) | NOT NULL | ''::character varying | 客户端Key |

## plm_menu

> 菜单权限表

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('plm_menu_id_seq'::regclass) | 菜单ID |
| `menu_name` | character varying(50) | NOT NULL | — | 菜单名称 |
| `parent_id` | bigint | NULL | 0 | 父菜单ID |
| `order_num` | integer | NULL | 0 | 显示顺序 |
| `path` | character varying(200) | NULL | ''::character varying | 路由地址 |
| `component` | character varying(255) | NULL | NULL::character varying | 组件路径 |
| `query_param` | character varying(255) | NULL | NULL::character varying | 路由参数 |
| `is_frame` | character(1) | NULL | 'N'::bpchar | 是否为外链（Y是 N否） |
| `is_cache` | character(1) | NULL | 'Y'::bpchar | 是否缓存（Y缓存 N不缓存） |
| `menu_type` | character(1) | NULL | ''::bpchar | 菜单类型（M目录 C菜单 F按钮） |
| `visible` | character(1) | NULL | '0'::bpchar | 显示状态（0显示 1隐藏） |
| `status` | character(1) | NULL | '0'::bpchar | 菜单状态（0正常 1停用） |
| `perms` | character varying(100) | NULL | NULL::character varying | 权限标识 |
| `icon` | character varying(100) | NULL | '#'::character varying | 菜单图标 |
| `active_menu` | character varying(255) | NULL | ''::character varying | 激活菜单路径 |
| `ext` | character varying(2000) | NULL | ''::character varying | 扩展字段 |
| `created_by` | bigint | NULL | — | 创建者 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_by` | bigint | NULL | — | 更新者 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `remark` | character varying(500) | NULL | ''::character varying | 备注 |

## plm_oper_log

> 操作日志记录

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_oper_log_id_seq'::regclass) | 日志主键 |
| `title` | character varying(50) | NULL | ''::character varying | 模块标题 |
| `business_type` | integer | NULL | 0 | 业务类型（0其它 1新增 2修改 3删除） |
| `method` | character varying(100) | NULL | ''::character varying | 方法名称 |
| `request_method` | character varying(10) | NULL | ''::character varying | 请求方式 |
| `operator_type` | integer | NULL | 0 | 操作类别（0其它 1后台用户 2手机端用户） |
| `oper_name` | character varying(50) | NULL | ''::character varying | 操作人员 |
| `user_id` | bigint | NOT NULL | — | 操作用户ID |
| `device_type` | character varying(32) | NULL | ''::character varying | 设备类型 |
| `browser` | character varying(50) | NULL | ''::character varying | 浏览器类型 |
| `os` | character varying(50) | NULL | ''::character varying | 操作系统 |
| `oper_url` | character varying(255) | NULL | ''::character varying | 请求URL |
| `oper_ip` | character varying(128) | NULL | ''::character varying | 主机地址 |
| `oper_location` | character varying(255) | NULL | ''::character varying | 操作地点 |
| `oper_param` | character varying(4000) | NULL | ''::character varying | 请求参数 |
| `json_result` | character varying(4000) | NULL | ''::character varying | 返回参数 |
| `status` | integer | NULL | 0 | 操作状态（0正常 1异常） |
| `error_msg` | character varying(4000) | NULL | ''::character varying | 错误消息 |
| `oper_time` | timestamp with time zone | NULL | — | 操作时间 |
| `cost_time` | bigint | NULL | 0 | 消耗时间 |
| `client_key` | character varying(32) | NOT NULL | ''::character varying | 客户端Key |

## plm_resource

> 通用资源存储

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('plm_resource_id_seq'::regclass) | 主键 |
| `storage_key` | character varying(512) | NOT NULL | — | 存储键（相对路径或对象Key） |
| `original_name` | character varying(255) | NOT NULL | — | 原始文件名 |
| `file_size` | bigint | NULL | 0 | 文件大小(bytes) |
| `mime_type` | character varying(128) | NULL | — | MIME类型 |
| `storage_type` | character varying(32) | NOT NULL | 'LOCAL'::character varying | 存储类型: LOCAL/MINIO |
| `access_url` | character varying(1024) | NULL | — | 访问URL |
| `biz_type` | character varying(64) | NOT NULL | 'GENERAL'::character varying | 业务类型: AVATAR/ATTACHMENT/DOCUMENT/GENERAL |
| `biz_id` | bigint | NULL | — | 关联业务ID |
| `created_by` | bigint | NULL | — | 创建者ID |
| `create_dt` | timestamp without time zone | NULL | CURRENT_TIMESTAMP | 创建时间 |
| `update_dt` | timestamp without time zone | NULL | CURRENT_TIMESTAMP | 更新时间 |

## plm_role

> 角色信息表

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('plm_role_id_seq'::regclass) | 角色ID |
| `role_name` | character varying(30) | NOT NULL | — | 角色名称 |
| `role_key` | character varying(100) | NOT NULL | — | 角色权限字符串 |
| `role_sort` | integer | NOT NULL | — | 显示顺序 |
| `data_scope` | character(1) | NULL | '1'::bpchar | 数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限 5：仅本人数据权限 6：部门及以下或本人数据权限） |
| `menu_check_strictly` | boolean | NULL | true | 菜单树选择项是否关联显示 |
| `dept_check_strictly` | boolean | NULL | true | 部门树选择项是否关联显示 |
| `status` | character(1) | NOT NULL | — | 角色状态（0正常 1停用） |
| `del_flag` | character(1) | NULL | '0'::bpchar | 删除标志（0代表存在 1代表删除） |
| `created_by` | bigint | NULL | — | 创建者 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_by` | bigint | NULL | — | 更新者 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `remark` | character varying(500) | NULL | NULL::character varying | 备注 |

## plm_role_menu

> 角色和菜单关联表

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `role_id` | bigint | NOT NULL | — | 角色ID |
| `menu_id` | bigint | NOT NULL | — | 菜单ID |

## plm_user

> 用户信息表

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('plm_user_id_seq'::regclass) | 用户ID |
| `user_name` | character varying(30) | NOT NULL | — | 用户账号 |
| `nick_name` | character varying(30) | NOT NULL | — | 用户昵称 |
| `user_type` | character varying(10) | NULL | 'sys_user'::character varying | 用户类型（sys_user系统用户） |
| `email` | character varying(50) | NULL | ''::character varying | 用户邮箱 |
| `phone_number` | character varying(11) | NULL | ''::character varying | 手机号码 |
| `gender` | character(1) | NULL | '0'::bpchar | 用户性别（0男 1女 2未知） |
| `avatar` | bigint | NULL | — | 头像地址 |
| `password` | character varying(100) | NULL | ''::character varying | 密码 |
| `status` | character(1) | NULL | '0'::bpchar | 账号状态（0正常 1停用） |
| `del_flag` | character(1) | NULL | '0'::bpchar | 删除标志（0代表存在 1代表删除） |
| `login_ip` | character varying(128) | NULL | ''::character varying | 最后登陆IP |
| `login_date` | timestamp without time zone | NULL | — | 最后登陆时间 |
| `created_by` | bigint | NULL | — | 创建者 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_by` | bigint | NULL | — | 更新者 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `remark` | character varying(500) | NULL | NULL::character varying | 备注 |

## plm_user_role

> 用户和角色关联表

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `user_id` | bigint | NOT NULL | — | 用户ID |
| `role_id` | bigint | NOT NULL | — | 角色ID |

## v5ai_agent

> Agent（智能体）：平台核心实体，agent_key 全局唯一；status 为 DRAFT/PUBLISHED/DISABLED；发布时把配置快照写入 v5ai_agent_version

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_application_id_seq'::regclass) | 主键 |
| `agent_key` | character varying(100) | NOT NULL | — | Agent 对外运行标识（全局唯一；运行时按它解析 Agent） |
| `name` | character varying(200) | NOT NULL | — | 名称 |
| `description` | text | NULL | — | 描述 |
| `model_id` | bigint | NULL | — | 绑定的模型（v5ai_model.id） |
| `status` | character varying(30) | NOT NULL | 'DRAFT'::character varying | 状态（DRAFT / PUBLISHED / DISABLED） |
| `published_version` | bigint | NULL | — | 当前生效的已发布版本号（未发布为 null） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `system_prompt` | text | NULL | — | 系统提示词（随发布版本固化） |
| `avatar` | character varying(512) | NULL | — | 头像URL |
| `greeting` | text | NULL | — | 欢迎语 |
| `preset_questions` | text | NULL | — | 预设问题列表（JSON数组字符串） |
| `memory_enabled` | boolean | NOT NULL | false | 是否启用记忆库 |
| `mcp_enabled` | boolean | NOT NULL | false | 是否启用MCP |
| `skill_enabled` | boolean | NOT NULL | false | 是否启用Skill |
| `web_search_enabled` | boolean | NOT NULL | false | 是否启用联网搜索 |
| `rag_enabled` | boolean | NOT NULL | false | 是否启用RAG |
| `rag_call_mode` | smallint | NOT NULL | 2 | RAG调用方式: 1=智能调用 2=强制调用 |
| `secondary_model_id` | bigint | NULL | — | 次要模型 id（v5ai_model.id，须为 CHAT 类型且已启用）：供会话标题改写与会话摘要压缩调用；为空则回退绑定的对话模型 |
| `show_citations` | boolean | NOT NULL | true | 是否在聊天窗口展示 RAG 引用折叠块；默认 true；仅影响渲染，引用照常检索与落库（见 docs/adr/0009） |

## v5ai_agent_knowledge

> Agent ↔ 知识库绑定：复合主键 (agent_key, knowledge_base_id)，无 updated_at 列

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `agent_key` | character varying(100) | NOT NULL | — | Agent 对外运行标识（跨模块引用，不建外键） |
| `knowledge_base_id` | bigint | NOT NULL | — | 绑定的知识库 ID |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 绑定创建时间 |

## v5ai_agent_mcp

> Agent ↔ MCP Server 绑定：复合主键 (agent_key, mcp_server_id)

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `agent_key` | character varying(100) | NOT NULL | — | Agent 对外运行标识（跨模块引用，不建外键） |
| `mcp_server_id` | bigint | NOT NULL | — | 绑定的 MCP Server |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 绑定创建时间 |

## v5ai_agent_skill

> Agent ↔ Skill 绑定：复合主键 (agent_key, skill_id)，无自增主键列

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `agent_key` | character varying(100) | NOT NULL | — | Agent 对外运行标识（跨模块引用，不建外键） |
| `skill_id` | bigint | NOT NULL | — | 绑定的 Skill |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 绑定创建时间 |

## v5ai_agent_state

> Agent 运行状态：按会话保存的运行态 JSON，一会话一行

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `conversation_id` | character varying(36) | NOT NULL | — | 所属会话（v5ai_conversation.id）；一会话一行 |
| `state_json` | text | NOT NULL | — | Agent 运行状态的 JSON 序列化结果（按会话滚动覆盖） |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |

## v5ai_agent_version

> Agent 发布版本：发布时固化的配置快照（snapshot_json），版本号在每个 Agent 内自增

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_application_version_id_seq'::regclass) | 主键 |
| `agent_key` | character varying(100) | NOT NULL | — | 所属 Agent 的对外标识（v5ai_agent.agent_key） |
| `version` | bigint | NOT NULL | — | 版本号（每个 AgentDTO 内自增，从 1 起） |
| `snapshot_json` | text | NOT NULL | — | 发布时固化的配置快照（JSON 文本） |
| `description` | text | NULL | — | 版本描述 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

## v5ai_api_keys

> Agent API Key：归属创建用户，可访问的 Agent 由 v5ai_api_keys_agent 绑定；库内只存摘要与密文（key_hash sha256 定位 / secret_hash BCrypt 校验）

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_api_key_id_seq'::regclass) | 主键 |
| `secret_hash` | character varying(255) | NOT NULL | — | API Key 密文（BCrypt） |
| `enabled` | boolean | NOT NULL | true | 是否启用 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `user_id` | bigint | NOT NULL | — | Key 归属用户（plm_user.id） |
| `name` | character varying(100) | NOT NULL | — | Key 名称（页面主标识，同一用户内唯一） |
| `tracking_id` | character varying(36) | NOT NULL | — | 跟踪 ID：独立 UUID，与明文 Key 无关联，仅供展示与日志/审计对账 |
| `last_used_at` | timestamp with time zone | NULL | — | 最新使用时间（鉴权成功时刷新） |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 修改时间 |
| `key_hash` | character varying(64) | NOT NULL | — | 明文 Key 的 SHA-256 摘要（小写十六进制），运行时唯一索引定位密钥行 |

## v5ai_api_keys_agent

> API Key ↔ Agent 绑定：某把 Key 可以访问哪些 Agent（仅已发布 Agent）

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_api_keys_agent_id_seq'::regclass) | 主键 |
| `api_key_id` | bigint | NOT NULL | — | v5ai_api_keys.id（Key 删除时级联删除绑定） |
| `agent_key` | character varying(100) | NOT NULL | — | v5ai_agent.agent_key（不建跨模块外键，删除 Agent 时显式清理） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 绑定创建时间（由数据库默认值 CURRENT_TIMESTAMP 填充，写入侧不赋值） |

## v5ai_app_quota

> 按 Agent 维度的调用配额：每日调用次数、每日 token、每分钟限流；0 一律表示不限制

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `agent_key` | character varying(100) | NOT NULL | — | Agent 对外运行标识（配额按 Agent 维度配置，本表主键） |
| `daily_model_calls` | integer | NOT NULL | 0 | 每日模型调用次数上限；0 = 不限制 |
| `daily_tokens` | bigint | NOT NULL | 0 | 每日 token 上限；0 = 不限制；当前未参与拦截（checkAllowed 只校验每分钟限流与每日调用次数） |
| `rate_per_minute` | integer | NOT NULL | 0 | 每分钟调用次数上限（进程内限流器）；0 = 不限制 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

## v5ai_app_usage

> 按 Agent 的每日用量累计：复合主键 (agent_key, usage_date)，由 upsert 累加；勿使用 updateById/deleteById

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `agent_key` | character varying(100) | NOT NULL | — | Agent 对外运行标识（复合主键之一） |
| `usage_date` | date | NOT NULL | — | 统计自然日（按服务端 LocalDate.now() 切分；复合主键之一） |
| `model_calls` | integer | NOT NULL | 0 | 当日模型调用次数（每次调用 upsert 累加 1） |
| `tokens` | bigint | NOT NULL | 0 | 当日累计 token（每次调用累加，负数按 0 计） |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

## v5ai_conversation

> 门户会话：归属主体是 API Key（api_key_id），user_id 只作展示冗余；调试入口产生的会话该列为 NULL

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | character varying(36) | NOT NULL | — | 会话 ID（UUID 字符串） |
| `agent_key` | character varying(100) | NOT NULL | — | Agent 对外运行标识（跨模块引用，不建外键；删除 Agent 时由 AgentCleanupMapper 显式清理） |
| `user_id` | bigint | NULL | — | 归属用户（展示与审计冗余，只写不判；归属判定一律以 api_key_id 为准，见 ADR-0006） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `api_key_id` | bigint | NULL | — | 会话归属的 API Key（门户侧租户边界）；调试入口产生的会话为 NULL，不属于任何 Key |
| `name` | character varying(100) | NULL | — | 会话名称：新建会话时由首条提问生成（ConversationNaming），首轮结束后可由模型改写成短标题；用户可改名（name_source=USER 后不再被自动覆盖） |
| `archived_at` | timestamp with time zone | NULL | — | 归档时间；非空表示已归档：列表默认隐藏且禁止继续对话 |
| `name_source` | character varying(16) | NULL | — | 名称来源：AUTO=系统生成（首条提问兜底名/模型标题，可被模型改写），USER=用户改名（永不被覆盖） |

## v5ai_conversation_summary

> 会话历史摘要：历史窗口之外的旧内容压缩成一段文本继续参与上下文；一会话一行（滚动覆盖）

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `conversation_id` | character varying(36) | NOT NULL | — | 所属会话（v5ai_conversation.id）；一会话一行 |
| `summary` | text | NOT NULL | — | 摘要正文（模型生成，长度上限见 v5ai.chat.conversation-summary.max-summary-chars） |
| `covered_until_message_id` | bigint | NOT NULL | — | 摘要覆盖水位：id ≤ 该值的消息都已并入摘要（重新生成锚点落在其中时整条摘要作废） |
| `covered_messages` | integer | NOT NULL | 0 | 已并入摘要的消息条数（观测用） |
| `model_id` | bigint | NULL | — | 生成该摘要的模型 id（审计用；配置了固定摘要模型时与 Agent 绑定模型不同） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |

## v5ai_knowledge_base

> 知识库：status 为 ACTIVE/DISABLED

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_knowledge_base_id_seq'::regclass) | 主键 |
| `name` | character varying(200) | NOT NULL | — | 知识库名称 |
| `description` | text | NULL | — | 知识库描述 |
| `status` | character varying(30) | NOT NULL | 'ACTIVE'::character varying | 启用状态（ACTIVE / DISABLED） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `icon` | character varying(512) | NULL | — | 图标 |
| `embedding_model_id` | bigint | NOT NULL | 0 | 向量模型id |
| `vector_store_instance_id` | bigint | NULL | — | 向量存储实例id |
| `dimension_of_vector_model` | integer | NOT NULL | 0 | 向量维度 |
| `rerank_model_id` | bigint | NULL | — | 重排序模型id |
| `search_engine_enable` | boolean | NULL | false | 搜索引擎启用标志 |
| `search_engine_instance_id` | bigint | NULL | — | 搜索引擎实例id |
| `delimiter` | character varying(32) | NULL | `'\n\n'::character varying` | 文档分割符 |
| `rag_enhancement` | text | NULL | — | RAG增强配置 |
| `config` | text | NULL | — | RAG检索和问答的页面配置参数（RagConfigDO JSON） |
| `dedup_strategy` | smallint | NOT NULL | 2 | 去重策略: 0=NONE 1=BY_NAME 2=BY_CONTENT 3=BY_NAME_OR_CONTENT |
| `dedup_action` | smallint | NOT NULL | 0 | 冲突动作: 0=REJECT 1=SKIP 2=OVERWRITE |

## v5ai_knowledge_chunk

> 知识库分片：检索单元；正文在 content、向量在向量库（vector_id）、关键词分词在 keyword_tokens

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_knowledge_chunk_id_seq'::regclass) | 主键 |
| `knowledge_base_id` | bigint | NOT NULL | — | 所属知识库（v5ai_knowledge_base.id） |
| `document_id` | bigint | NOT NULL | — | 所属文档（v5ai_knowledge_document.id） |
| `chunk_index` | integer | NOT NULL | — | 文档内分片序号（从 0 起） |
| `content` | text | NOT NULL | — | 分片正文 |
| `metadata` | jsonb | NULL | — | 分片元数据 JSON：{"documentId","chunkIndex","title"}；由索引 Worker 写入，缺省 {} |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `paragraph_index` | integer | NULL | — | 段落索引 |
| `token_count` | integer | NULL | — | 分片token数量 |
| `vector_id` | character varying(128) | NULL | — | 向量id |
| `content_hash` | character varying(64) | NULL | — | chunk内容SHA-256，用于向量去重 |
| `source_type` | character varying(20) | NOT NULL | 'TEXT'::character varying | chunk来源类型: TEXT=文本 IMAGE=图片 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `keyword_tokens` | text[] | NULL | — | 关键词分词（jieba INDEX 模式，保留重复词；PG text[]）；仅服务关键词路 BM25：词频 tf 由数组内出现次数现算、文档长度 dl = 数组长度、df/avgdl 检索时按库集合现算；写入由 Worker 重建与手工切片增改维护 |

## v5ai_knowledge_document

> 知识库文档：content 存原始内容（BYTEA）、parsed_text 为解析后纯文本；status 为索引状态（0 待处理 / 1 解析中 / 2 处理中 / 3 完成 / 4 失败）

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_knowledge_document_id_seq'::regclass) | 主键 |
| `knowledge_base_id` | bigint | NOT NULL | — | 所属知识库（v5ai_knowledge_base.id） |
| `title` | character varying(300) | NOT NULL | — | 文档标题 |
| `file_type` | character varying(30) | NOT NULL | — | 文件类型（TXT / MARKDOWN / PDF / DOCX / URL） |
| `source_type` | character varying(30) | NOT NULL | 'UPLOAD'::character varying | 来源类型: UPLOAD=上传 URL=网络 |
| `status` | smallint | NOT NULL | 0 | 索引状态: 0=待处理 1=解析中 2=处理中 3=处理完成 4=处理失败 |
| `content` | bytea | NULL | — | 原始内容（BYTEA） |
| `parsed_text` | text | NULL | — | 解析后的纯文本 |
| `error_message` | text | NULL | — | 失败时的错误信息 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `storage_type` | character varying(32) | NOT NULL | 'LOCAL'::character varying | 存储类型: LOCAL=本地 MINIO=minio |
| `storage_path` | character varying(1024) | NULL | — | 存储路径 |
| `file_size` | bigint | NOT NULL | 0 | 文件大小(bytes) |
| `chunk_count` | integer | NOT NULL | 0 | 分片数量 |
| `parse_time` | integer | NOT NULL | 0 | 解析耗时（毫秒） |
| `content_hash` | character varying(64) | NULL | — | 文件内容SHA-256哈希，用于去重 |
| `resource_id` | bigint | NULL | — | 关联资源库 plm_resource.id |

## v5ai_knowledge_task

> 知识库索引任务：由 Worker 异步消费；status 为 PENDING/PROCESSING/COMPLETED/FAILED，带重试计数

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_knowledge_task_id_seq'::regclass) | 主键 |
| `knowledge_base_id` | bigint | NOT NULL | — | 所属知识库（v5ai_knowledge_base.id） |
| `document_id` | bigint | NOT NULL | — | 关联文档（v5ai_knowledge_document.id） |
| `task_type` | character varying(30) | NOT NULL | 'PARSE_AND_INDEX'::character varying | 任务类型（如 PARSE_AND_INDEX） |
| `status` | character varying(30) | NOT NULL | 'PENDING'::character varying | 任务状态（PENDING / PROCESSING / COMPLETED / FAILED） |
| `attempt_count` | integer | NOT NULL | 0 | 已尝试次数 |
| `max_attempts` | integer | NOT NULL | 3 | 最大尝试次数 |
| `error_message` | text | NULL | — | 失败时的错误信息 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |

## v5ai_mcp_server

> MCP Server 注册：Streamable HTTP / SSE / Stdio；headers/env 为密文列

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_mcp_server_id_seq'::regclass) | 主键 |
| `name` | character varying(200) | NOT NULL | — | 平台内唯一标识名（也是 MCP 客户端名） |
| `transport_type` | character varying(30) | NOT NULL | — | 传输类型（STREAMABLE_HTTP / SSE / STDIO） |
| `endpoint` | character varying(1000) | NULL | — | HTTP 传输为 URL；Stdio 传输为可执行命令 |
| `args_json` | text | NULL | — | Stdio 命令参数（JSON 文本） |
| `headers_ciphertext` | text | NULL | — | HTTP 请求头密文（明文 JSON 加密后落库） |
| `env_ciphertext` | text | NULL | — | Stdio 环境变量密文（明文 JSON 加密后落库） |
| `timeout_seconds` | integer | NOT NULL | 30 | 请求超时秒数 |
| `status` | character varying(30) | NOT NULL | 'ACTIVE'::character varying | 启用状态（ACTIVE / DISABLED） |
| `last_test_status` | character varying(30) | NULL | — | 最近一次连接测试结果（ok/failed，可为 null） |
| `last_test_message` | text | NULL | — | 最近一次连接测试消息（可为 null） |
| `last_tested_at` | timestamp with time zone | NULL | — | 最近一次连接测试时间（可为 null） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |

## v5ai_mcp_tool

> MCP Tool 缓存：来自 Tool 发现结果；permission 为平台权限决策（ALLOW / APPROVE / DENY）

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_mcp_tool_id_seq'::regclass) | 主键 |
| `server_id` | bigint | NOT NULL | — | 所属 MCP Server |
| `tool_name` | character varying(300) | NOT NULL | — | 工具名（MCP 协议内唯一，与 serverId 组成唯一键） |
| `description` | text | NULL | — | 工具描述 |
| `input_schema_json` | text | NULL | — | 输入 JSON Schema（JSON 文本） |
| `read_only` | boolean | NOT NULL | false | 服务器声明的只读提示 |
| `permission` | character varying(30) | NOT NULL | 'ALLOW'::character varying | 平台权限决策（ALLOW / APPROVE / DENY） |
| `last_discovered_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 最近一次发现时间 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |

## v5ai_mcp_tool_call

> MCP Tool 调用审计：每次工具调用落库，用于追溯与合规

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_mcp_tool_call_id_seq'::regclass) | 主键 |
| `run_id` | character varying(64) | NOT NULL | — | 所属运行 ID |
| `server_id` | bigint | NOT NULL | — | 所属 MCP Server |
| `server_name` | character varying(200) | NOT NULL | — | MCP Server 名称（冗余存储，便于审计查询） |
| `tool_name` | character varying(300) | NOT NULL | — | 工具名 |
| `arguments_summary` | text | NULL | — | 参数摘要（截断，不落完整敏感参数） |
| `decision` | character varying(30) | NOT NULL | — | 平台权限决策（ALLOW / APPROVE / DENY） |
| `status` | character varying(30) | NOT NULL | — | 调用结果状态 |
| `duration_ms` | bigint | NULL | — | 调用耗时（毫秒） |
| `message` | text | NULL | — | 错误信息（失败时） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 记录时间 |

## v5ai_message

> 会话消息：role 为 USER/ASSISTANT；superseded_at 非空表示已被「重新生成」作废，不参与展示与记忆回放

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_message_id_seq'::regclass) | 主键 |
| `conversation_id` | character varying(36) | NOT NULL | — | 所属会话（v5ai_conversation.id） |
| `agent_key` | character varying(100) | NULL | — | Agent 对外运行标识（跨模块引用，不建外键） |
| `role` | character varying(30) | NOT NULL | — | 消息角色（USER / ASSISTANT） |
| `content` | text | NOT NULL | — | 消息正文 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `prompt_tokens` | integer | NULL | — | 该回答的输入 token：模型回报的真实值优先，未回报时回退为平台字符估算；仅助手消息有值 |
| `completion_tokens` | integer | NULL | — | 该回答的输出 token：口径同上；仅助手消息有值 |
| `duration_ms` | integer | NULL | — | 该次运行的服务端耗时（毫秒，RUN_STARTED 到收尾）；仅助手消息有值 |
| `superseded_at` | timestamp with time zone | NULL | — | 作废时间；非空表示该消息已被「重新生成」替换：不参与展示与记忆回放，但数据保留 |
| `reasoning` | text | NULL | — | 模型的思考过程（仅助手消息有值；NULL=没有思考或 V42 之前的历史数据）；供门户回看，不回放给模型 |
| `metadata` | text | NULL | — | 消息扩展元数据（JSON 文本）：当前语义为引用快照 {"citations":[...]}，仅助手消息有值；NULL=这一轮没有引用；供门户回看，不回放给模型 |

## v5ai_message_attachment

> 消息携带的附件（图片）：resource_id 指向 plm_resource 中的 ATTACHMENT 资源

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_message_attachment_id_seq'::regclass) | 主键 |
| `message_id` | bigint | NOT NULL | — | 所属消息（v5ai_message.id） |
| `resource_id` | bigint | NOT NULL | — | 资源标识（plm_resource.id，biz_type=ATTACHMENT） |
| `type` | character varying(32) | NOT NULL | — | 附件类型，当前只有 IMAGE |
| `ordinal` | integer | NOT NULL | 0 | 同一条消息内附件的展示/回放顺序（从 0 起） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

## v5ai_model

> 模型配置以 id 作为平台身份；同一 provider 下允许多个相同 model_key 的配置，用于不同账户、Base URL 或服务器；凭据加密存储，config 为扩展参数 JSON

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_model_id_seq'::regclass) | 主键 |
| `provider_id` | bigint | NOT NULL | — | 提供商 ID |
| `model_key` | character varying(200) | NOT NULL | — | 模型密钥 |
| `model_type` | character varying(40) | NOT NULL | — | 模型类型(CHAT/EMBEDDING/RERANKER/IMAGE/SPEECH) |
| `base_url` | character varying(500) | NULL | — | API 基础地址(与凭据 JSON 的 baseUrl 键一致, 如 https://api.deepseek.com/v1) |
| `credentials_ciphertext` | text | NULL | — | 凭据加密存储 |
| `enabled` | boolean | NOT NULL | true | 是否启用 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `model_name` | character varying(255) | NOT NULL | — | 模型名称 |
| `description` | character varying(1000) | NULL | — | 模型描述 |
| `adapter_key` | character varying(100) | NULL | — | 底层协议适配器标识(openai-compatible/http等) |
| `config` | text | NULL | — | 模型参数配置(JSON格式) |
| `scope` | character varying(20) | NOT NULL | 'GLOBAL'::character varying | 模型作用域: GLOBAL=全局 PERSONAL=个人 |
| `is_default` | boolean | NOT NULL | false | 是否为默认模型 |
| `owner_id` | bigint | NULL | — | 所有者ID(NULL=全局,具体值=用户ID) |

## v5ai_model_provider

> 模型供应商：provider_key 全局唯一，可配置描述与图标

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_model_provider_id_seq'::regclass) | 主键 |
| `provider_key` | character varying(100) | NOT NULL | — | 供应商对外标识（全局唯一） |
| `name` | character varying(200) | NOT NULL | — | 供应商名称 |
| `enabled` | boolean | NOT NULL | true | 是否启用 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `description` | text | NULL | — | 提供商描述 |
| `icon_url` | character varying(500) | NULL | — | LOGO图标URL |

## v5ai_model_usage

> 模型用量明细账（可观测性统计）：每次模型调用一行；服务端未回报用量时按平台规则估算

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_model_usage_id_seq'::regclass) | 主键 |
| `run_id` | character varying(64) | NULL | — | 关联运行（v5ai_run.id；非运行期调用为 NULL） |
| `agent_key` | character varying(100) | NULL | — | Agent 对外运行标识（跨模块引用，不建外键） |
| `model_id` | bigint | NULL | — | 平台模型配置 ID；历史记录可能为空，用于用量聚合区分重复 model_key |
| `model_key` | character varying(200) | NULL | — | 被调用模型标识 |
| `prompt_tokens` | bigint | NOT NULL | 0 | 输入 token（真实用量优先，服务端未回报时按平台规则估算） |
| `completion_tokens` | bigint | NOT NULL | 0 | 输出 token（口径同 prompt_tokens） |
| `total_tokens` | bigint | NOT NULL | 0 | 总 token（输入 + 输出） |
| `duration_ms` | bigint | NULL | — | 本次调用耗时（毫秒） |
| `status` | character varying(30) | NOT NULL | 'SUCCESS'::character varying | 调用结果状态（默认 SUCCESS） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

## v5ai_run

> 一次 Agent 运行：id 为 UUID 字符串，status 记录运行终态

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | character varying(36) | NOT NULL | — | 运行 ID（UUID 字符串） |
| `conversation_id` | character varying(36) | NOT NULL | — | 所属会话（v5ai_conversation.id） |
| `agent_key` | character varying(100) | NOT NULL | — | Agent 对外运行标识（跨模块引用，不建外键） |
| `status` | character varying(30) | NOT NULL | — | 运行状态 |
| `error_message` | text | NULL | — | 错误信息 |
| `started_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 开始时间 |
| `completed_at` | timestamp with time zone | NULL | — | 完成时间 |

## v5ai_run_event

> 运行事件流水：SSE 事件的落库副本（不含思考过程，也不含内部用量事件 MODEL_USAGE）

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_run_event_id_seq'::regclass) | 主键 |
| `run_id` | character varying(36) | NOT NULL | — | 所属运行（v5ai_run.id） |
| `event_type` | character varying(50) | NOT NULL | — | 事件类型（RuntimeEventType 枚举名：RUN_STARTED / MODEL_CALL / TEXT_DELTA / TOOL_CALL / TOOL_RESULT / RETRIEVAL / MESSAGE_COMPLETED / RUN_COMPLETED / RUN_FAILED / PERMISSION_REQUIRED） |
| `payload` | text | NULL | — | 事件负载（JSON 文本；RUN_STARTED 为提问消息 id + 上下文窗口统计，RUN_COMPLETED 为本次用量汇总） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

## v5ai_skill

> Skill：技能名全局唯一（取自 SKILL.md frontmatter），status 为 ACTIVE/DISABLED

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_skill_id_seq'::regclass) | 主键 |
| `name` | character varying(200) | NOT NULL | — | 技能名（来自 SKILL.md frontmatter，全局唯一） |
| `description` | text | NULL | — | 技能描述 |
| `status` | character varying(30) | NOT NULL | 'ACTIVE'::character varying | 启用状态（ACTIVE / DISABLED） |
| `current_version_id` | bigint | NULL | — | 当前生效（注入运行时）的已发布版本 ID |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |

## v5ai_skill_file

> Skill 包文件：发布版本的原始文件（SKILL.md、prompts/、resources/ 等），运行时据此注入 Workspace

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_skill_file_id_seq'::regclass) | 主键 |
| `skill_id` | bigint | NOT NULL | — | 所属 Skill |
| `version_id` | bigint | NOT NULL | — | 所属版本 |
| `file_path` | character varying(500) | NOT NULL | — | 包内相对路径（如 "SKILL.md"、"prompts/guide.md"） |
| `content` | text | NOT NULL | — | 文本内容（UTF-8） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

## v5ai_skill_version

> Skill 版本：DRAFT/PUBLISHED/OFFLINE；current_version_id 指向运行时注入的版本

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_skill_version_id_seq'::regclass) | 主键 |
| `skill_id` | bigint | NOT NULL | — | 所属 Skill |
| `version` | bigint | NOT NULL | — | 版本号（每个 Skill 内自增） |
| `status` | character varying(30) | NOT NULL | 'DRAFT'::character varying | 状态（DRAFT / PUBLISHED / OFFLINE） |
| `description` | text | NULL | — | 版本描述 |
| `published_at` | timestamp with time zone | NULL | — | 发布时间（未发布为 null） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |

## v5ai_store_instance

> 存储实例

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_store_instance_id_seq'::regclass) | 主键 |
| `name` | character varying(128) | NOT NULL | — | 实例名称 |
| `category` | smallint | NOT NULL | — | 分类: 1-向量库 2-搜索引擎 |
| `type` | smallint | NOT NULL | — | 类型: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-PG_FULLTEXT |
| `config` | text | NULL | — | 连接参数 JSON |
| `status` | smallint | NULL | 1 | 状态: 0-停用 1-启用 |
| `is_default` | boolean | NULL | false | 是否为该 category 下默认实例 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |
| `description` | character varying(1000) | NULL | — | 实例描述 |

## v5ai_workflow

> Workflow：草稿与已发布定义分别以 JSON 文本存储

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_workflow_id_seq'::regclass) | 主键 |
| `workflow_key` | character varying(100) | NOT NULL | — | 对外标识（全局唯一） |
| `name` | character varying(200) | NOT NULL | — | 名称 |
| `description` | text | NULL | — | 描述 |
| `status` | character varying(30) | NOT NULL | 'DRAFT'::character varying | 状态（DRAFT / PUBLISHED / DISABLED） |
| `draft_definition` | text | NULL | — | 草稿定义 JSON（{ nodes, edges }） |
| `published_definition` | text | NULL | — | 已发布定义 JSON（{ nodes, edges }） |
| `published_version` | bigint | NULL | — | 已发布版本号 |
| `published_at` | timestamp with time zone | NULL | — | 发布时间 |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 更新时间 |

## v5ai_workflow_node_run

> Workflow 节点运行：每次节点执行的输入输出与耗时，无 updated_at 列

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `id` | bigint | NOT NULL | nextval('v5ai_workflow_node_run_id_seq'::regclass) | 主键 |
| `run_id` | character varying(36) | NOT NULL | — | 所属 Workflow 运行（v5ai_workflow_run.run_id） |
| `node_id` | character varying(100) | NOT NULL | — | 流程定义中的节点 ID（definition JSON 内的节点 key，非自增列） |
| `node_type` | character varying(30) | NOT NULL | — | 节点类型（START / AGENT / CONDITION / END） |
| `status` | character varying(30) | NOT NULL | 'PENDING'::character varying | 状态（PENDING / RUNNING / SUCCEEDED / FAILED / SKIPPED） |
| `inputs` | text | NULL | — | 节点输入 JSON |
| `outputs` | text | NULL | — | 节点输出 JSON |
| `error` | text | NULL | — | 失败信息（成功为 NULL） |
| `started_at` | timestamp with time zone | NULL | — | 节点开始执行时间 |
| `finished_at` | timestamp with time zone | NULL | — | 节点结束时间（未结束为 NULL） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

## v5ai_workflow_run

> Workflow 运行：run_id 为 VARCHAR 主键，锁定运行时的已发布版本号

| 列 | 类型 | 约束 | 默认值 | 注释 |
|---|---|---|---|---|
| `run_id` | character varying(36) | NOT NULL | — | 运行 ID（VARCHAR 主键，UUID 字符串） |
| `workflow_key` | character varying(100) | NOT NULL | — | 被运行的 Workflow 对外标识（v5ai_workflow.workflow_key） |
| `workflow_version` | bigint | NULL | — | 运行时锁定的已发布版本号（v5ai_workflow.published_version） |
| `status` | character varying(30) | NOT NULL | 'RUNNING'::character varying | 状态（RUNNING / SUCCEEDED / FAILED） |
| `inputs` | text | NULL | — | 运行输入 JSON |
| `outputs` | text | NULL | — | 运行输出 JSON |
| `error` | text | NULL | — | 失败信息（成功为 NULL） |
| `started_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 运行开始时间 |
| `finished_at` | timestamp with time zone | NULL | — | 运行结束时间（未结束为 NULL） |
| `created_at` | timestamp with time zone | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
