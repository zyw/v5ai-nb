-- =============================================================================
-- MySQL 基线：等价于 PostgreSQL 侧迁移头（V1–V49）应用完的 schema 全貌
--
-- 为什么是基线而不是 49 个历史迁移：MySQL 侧没有存量数据要搬（见
-- docs/adr/0012-multi-dialect-database-support.md），逐条翻译历史迁移只会得到
-- 同样的最终态、却带来 49 倍的漂移面。列/类型/可空/默认/注释取自
-- docs/db/schema.md，索引与唯一键取自 postgresql/ 目录的迁移原文。
--
-- 类型映射（PG → MySQL 8.0）
--   BIGSERIAL + nextval        → BIGINT NOT NULL AUTO_INCREMENT
--   timestamp with time zone   → DATETIME(3)（无时区列；连接串必须带 serverTimezone，
--                                与 PG 的 timestamptz 语义差已在 ADR-0012 记录）
--   timestamp without time zone→ DATETIME
--   character varying(n)/text  → VARCHAR(n)/TEXT
--   character(1) (bpchar)      → CHAR(1)
--   boolean                    → TINYINT(1)（默认值 true/false → 1/0）
--   jsonb                      → JSON
--   bytea                      → LONGBLOB
--   text[] (keyword_tokens)    → JSON（数组语义不变，检索侧改用 JSON_OVERLAPS 等，见 M3）
--   CURRENT_TIMESTAMP 默认值    → CURRENT_TIMESTAMP(3)（MySQL 要求 fsp 与列一致）
--
-- 版本下限：MySQL 8.0.17（keyword_tokens 的多值函数索引需要 8.0.17+；
-- JSON_TABLE / JSON_OVERLAPS 分别需要 8.0.4 / 8.0.17）。
--
-- 与 PG 的四处刻意差异（都不是遗漏）：
--   1) 排序规则取 utf8mb4_0900_as_cs（区分大小写/重音），与 PG 现网一致；
--      MySQL 默认的 _ai_ci 会放宽唯一键与登录名的匹配语义。
--   2) PG 的 V39 部分索引 `WHERE superseded_at IS NULL` 在 MySQL 无对应物，
--      退化为同列序的普通复合索引（只影响索引体积/选择率，不影响结果）。
--   3) PG 的 V2 pgvector 扩展与 ivfflat 索引不建：向量自 V30 起就不在业务库，
--      MySQL 部署下向量检索须绑定独立向量存储实例（Milvus / Elasticsearch / pgvector 实例）。
--   4) InnoDB 强制为每个外键列建支撑索引（PG 不建），所以「只带外键、PG 侧没有
--      显式索引」的列（如 v5ai_run_event.run_id、v5ai_skill_file.skill_id）在 MySQL
--      会多出一个与约束同名的索引。这是引擎要求，不是照抄漏了——对账 schema 时按
--      「外键列的多余索引」解释即可，不要为了对齐去删（删了 InnoDB 会拒绝建外键）。
--
-- 幂等性：基线只在全新空库执行一次（Flyway 记账后不再重放），因此用
-- CREATE TABLE IF NOT EXISTS 兜住「同一库上手工重跑」的情形。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 平台域（plm_*）
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `plm_client` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `client_id`      VARCHAR(64)  NULL DEFAULT '' COMMENT '客户端id',
    `client_key`     VARCHAR(32)  NULL DEFAULT '' COMMENT '客户端key',
    `client_secret`  VARCHAR(255) NULL DEFAULT '' COMMENT '客户端秘钥',
    `grant_type`     VARCHAR(255) NULL DEFAULT '' COMMENT '授权类型',
    `device_type`    VARCHAR(32)  NULL DEFAULT '' COMMENT '设备类型',
    `access_path`    VARCHAR(2000) NULL DEFAULT '' COMMENT '允许访问路径',
    `ip_whitelist`   VARCHAR(1000) NULL DEFAULT '' COMMENT 'IP白名单',
    `active_timeout` INT          NULL DEFAULT 1800 COMMENT 'token活跃超时时间',
    `timeout`        INT          NULL DEFAULT 604800 COMMENT 'token固定超时',
    `status`         CHAR(1)      NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `del_flag`       CHAR(1)      NULL DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
    `created_by`     BIGINT       NULL COMMENT '创建者',
    `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by`     BIGINT       NULL COMMENT '更新者',
    `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='系统授权表';

CREATE TABLE IF NOT EXISTS `plm_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `user_name`   VARCHAR(30)  NOT NULL COMMENT '用户账号',
    `nick_name`   VARCHAR(30)  NOT NULL COMMENT '用户昵称',
    `user_type`   VARCHAR(10)  NULL DEFAULT 'sys_user' COMMENT '用户类型（sys_user系统用户）',
    `email`       VARCHAR(50)  NULL DEFAULT '' COMMENT '用户邮箱',
    `phone_number` VARCHAR(11) NULL DEFAULT '' COMMENT '手机号码',
    `gender`      CHAR(1)      NULL DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
    `avatar`      BIGINT       NULL COMMENT '头像地址',
    `password`    VARCHAR(100) NULL DEFAULT '' COMMENT '密码',
    `status`      CHAR(1)      NULL DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
    `del_flag`    CHAR(1)      NULL DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
    `login_ip`    VARCHAR(128) NULL DEFAULT '' COMMENT '最后登陆IP',
    `login_date`  DATETIME     NULL COMMENT '最后登陆时间',
    `created_by`  BIGINT       NULL COMMENT '创建者',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by`  BIGINT       NULL COMMENT '更新者',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `remark`      VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_plm_user_user_name` (`user_name`),
    KEY `idx_plm_user_phone` (`phone_number`),
    KEY `idx_plm_user_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='用户信息表';

CREATE TABLE IF NOT EXISTS `plm_role` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name`           VARCHAR(30)  NOT NULL COMMENT '角色名称',
    `role_key`            VARCHAR(100) NOT NULL COMMENT '角色权限字符串',
    `role_sort`           INT          NOT NULL COMMENT '显示顺序',
    `data_scope`          CHAR(1)      NULL DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限 5：仅本人数据权限 6：部门及以下或本人数据权限）',
    `menu_check_strictly` TINYINT(1)   NULL DEFAULT 1 COMMENT '菜单树选择项是否关联显示',
    `dept_check_strictly` TINYINT(1)   NULL DEFAULT 1 COMMENT '部门树选择项是否关联显示',
    `status`              CHAR(1)      NOT NULL COMMENT '角色状态（0正常 1停用）',
    `del_flag`            CHAR(1)      NULL DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
    `created_by`          BIGINT       NULL COMMENT '创建者',
    `created_at`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by`          BIGINT       NULL COMMENT '更新者',
    `updated_at`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `remark`              VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_plm_role_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='角色信息表';

CREATE TABLE IF NOT EXISTS `plm_menu` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `menu_name`   VARCHAR(50)  NOT NULL COMMENT '菜单名称',
    `parent_id`   BIGINT       NULL DEFAULT 0 COMMENT '父菜单ID',
    `order_num`   INT          NULL DEFAULT 0 COMMENT '显示顺序',
    `path`        VARCHAR(200) NULL DEFAULT '' COMMENT '路由地址',
    `component`   VARCHAR(255) NULL COMMENT '组件路径',
    `query_param` VARCHAR(255) NULL COMMENT '路由参数',
    `is_frame`    CHAR(1)      NULL DEFAULT 'N' COMMENT '是否为外链（Y是 N否）',
    `is_cache`    CHAR(1)      NULL DEFAULT 'Y' COMMENT '是否缓存（Y缓存 N不缓存）',
    `menu_type`   CHAR(1)      NULL DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
    `visible`     CHAR(1)      NULL DEFAULT '0' COMMENT '显示状态（0显示 1隐藏）',
    `status`      CHAR(1)      NULL DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
    `perms`       VARCHAR(100) NULL COMMENT '权限标识',
    `icon`        VARCHAR(100) NULL DEFAULT '#' COMMENT '菜单图标',
    `active_menu` VARCHAR(255) NULL DEFAULT '' COMMENT '激活菜单路径',
    `ext`         VARCHAR(2000) NULL DEFAULT '' COMMENT '扩展字段',
    `created_by`  BIGINT       NULL COMMENT '创建者',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by`  BIGINT       NULL COMMENT '更新者',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `remark`      VARCHAR(500) NULL DEFAULT '' COMMENT '备注',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='菜单权限表';

CREATE TABLE IF NOT EXISTS `plm_user_role` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_plm_user_role_rid` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='用户和角色关联表';

CREATE TABLE IF NOT EXISTS `plm_role_menu` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='角色和菜单关联表';

CREATE TABLE IF NOT EXISTS `plm_resource` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `storage_key`   VARCHAR(512)  NOT NULL COMMENT '存储键（相对路径或对象Key）',
    `original_name` VARCHAR(255)  NOT NULL COMMENT '原始文件名',
    `file_size`     BIGINT        NULL DEFAULT 0 COMMENT '文件大小(bytes)',
    `mime_type`     VARCHAR(128)  NULL COMMENT 'MIME类型',
    `storage_type`  VARCHAR(32)   NOT NULL DEFAULT 'LOCAL' COMMENT '存储类型: LOCAL/MINIO',
    `access_url`    VARCHAR(1024) NULL COMMENT '访问URL',
    `biz_type`      VARCHAR(64)   NOT NULL DEFAULT 'GENERAL' COMMENT '业务类型: AVATAR/ATTACHMENT/DOCUMENT/GENERAL',
    `biz_id`        BIGINT        NULL COMMENT '关联业务ID',
    `created_by`    BIGINT        NULL COMMENT '创建者ID',
    `create_dt`     DATETIME      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_dt`     DATETIME      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_storage_key` (`storage_key`),
    KEY `idx_plm_resource_original_name` (`original_name`),
    KEY `idx_plm_resource_biz_type` (`biz_type`),
    KEY `idx_plm_resource_create_dt` (`create_dt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='通用资源存储';

CREATE TABLE IF NOT EXISTS `plm_oper_log` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志主键',
    `title`         VARCHAR(50)   NULL DEFAULT '' COMMENT '模块标题',
    `business_type` INT           NULL DEFAULT 0 COMMENT '业务类型（0其它 1新增 2修改 3删除）',
    `method`        VARCHAR(100)  NULL DEFAULT '' COMMENT '方法名称',
    `request_method` VARCHAR(10)  NULL DEFAULT '' COMMENT '请求方式',
    `operator_type` INT           NULL DEFAULT 0 COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
    `oper_name`     VARCHAR(50)   NULL DEFAULT '' COMMENT '操作人员',
    `user_id`       BIGINT        NOT NULL COMMENT '操作用户ID',
    `device_type`   VARCHAR(32)   NULL DEFAULT '' COMMENT '设备类型',
    `browser`       VARCHAR(50)   NULL DEFAULT '' COMMENT '浏览器类型',
    `os`            VARCHAR(50)   NULL DEFAULT '' COMMENT '操作系统',
    `oper_url`      VARCHAR(255)  NULL DEFAULT '' COMMENT '请求URL',
    `oper_ip`       VARCHAR(128)  NULL DEFAULT '' COMMENT '主机地址',
    `oper_location` VARCHAR(255)  NULL DEFAULT '' COMMENT '操作地点',
    `oper_param`    VARCHAR(4000) NULL DEFAULT '' COMMENT '请求参数',
    `json_result`   VARCHAR(4000) NULL DEFAULT '' COMMENT '返回参数',
    `status`        INT           NULL DEFAULT 0 COMMENT '操作状态（0正常 1异常）',
    `error_msg`     VARCHAR(4000) NULL DEFAULT '' COMMENT '错误消息',
    `oper_time`     DATETIME(3)   NULL COMMENT '操作时间',
    `cost_time`     BIGINT        NULL DEFAULT 0 COMMENT '消耗时间',
    `client_key`    VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '客户端Key',
    PRIMARY KEY (`id`),
    KEY `idx_v5ai_oper_log_bt` (`business_type`),
    KEY `idx_v5ai_oper_log_uid` (`user_id`),
    KEY `idx_v5ai_oper_log_s` (`status`),
    KEY `idx_v5ai_oper_log_ot` (`oper_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='操作日志记录';

CREATE TABLE IF NOT EXISTS `plm_login_info` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '访问ID',
    `user_name`      VARCHAR(50)  NULL DEFAULT '' COMMENT '用户账号',
    `device_type`    VARCHAR(32)  NULL DEFAULT '' COMMENT '设备类型',
    `ipaddr`         VARCHAR(128) NULL DEFAULT '' COMMENT '登录IP地址',
    `login_location` VARCHAR(255) NULL DEFAULT '' COMMENT '登录地点',
    `browser`        VARCHAR(50)  NULL DEFAULT '' COMMENT '浏览器类型',
    `os`             VARCHAR(50)  NULL DEFAULT '' COMMENT '操作系统',
    `status`         VARCHAR(30)  NULL DEFAULT '0' COMMENT '登录状态（0正常 1异常）',
    `msg`            VARCHAR(255) NULL DEFAULT '' COMMENT '提示消息',
    `login_time`     DATETIME(3)  NULL COMMENT '访问时间',
    `client_key`     VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '客户端Key',
    PRIMARY KEY (`id`),
    KEY `idx_v5ai_login_info_s` (`status`),
    KEY `idx_v5ai_login_info_lt` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='系统访问记录';

-- -----------------------------------------------------------------------------
-- 模型与 Agent
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `v5ai_model_provider` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `provider_key` VARCHAR(100) NOT NULL COMMENT '供应商对外标识（全局唯一）',
    `name`         VARCHAR(200) NOT NULL COMMENT '供应商名称',
    `enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `description`  TEXT         NULL COMMENT '提供商描述',
    `icon_url`     VARCHAR(500) NULL COMMENT 'LOGO图标URL',
    PRIMARY KEY (`id`),
    UNIQUE KEY `v5ai_model_provider_provider_key_key` (`provider_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='模型供应商：provider_key 全局唯一，可配置描述与图标';

CREATE TABLE IF NOT EXISTS `v5ai_model` (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `provider_id`           BIGINT        NOT NULL COMMENT '提供商 ID',
    `model_key`             VARCHAR(200)  NOT NULL COMMENT '模型密钥',
    `model_type`            VARCHAR(40)   NOT NULL COMMENT '模型类型(CHAT/EMBEDDING/RERANKER/IMAGE/SPEECH)',
    `base_url`              VARCHAR(500)  NULL COMMENT 'API 基础地址(与凭据 JSON 的 baseUrl 键一致, 如 https://api.deepseek.com/v1)',
    `credentials_ciphertext` TEXT         NULL COMMENT '凭据加密存储',
    `enabled`               TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `model_name`            VARCHAR(255)  NOT NULL COMMENT '模型名称',
    `description`           VARCHAR(1000) NULL COMMENT '模型描述',
    `adapter_key`           VARCHAR(100)  NULL COMMENT '底层协议适配器标识(openai-compatible/http等)',
    `config`                TEXT          NULL COMMENT '模型参数配置(JSON格式)',
    `scope`                 VARCHAR(20)   NOT NULL DEFAULT 'GLOBAL' COMMENT '模型作用域: GLOBAL=全局 PERSONAL=个人',
    `is_default`            TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否为默认模型',
    `owner_id`              BIGINT        NULL COMMENT '所有者ID(NULL=全局,具体值=用户ID)',
    PRIMARY KEY (`id`),
    KEY `idx_v5ai_model_provider_key` (`provider_id`, `model_key`),
    CONSTRAINT `v5ai_model_provider_id_fkey` FOREIGN KEY (`provider_id`) REFERENCES `v5ai_model_provider` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='模型配置以 id 作为平台身份；同一 provider 下允许多个相同 model_key 的配置，用于不同账户、Base URL 或服务器；凭据加密存储，config 为扩展参数 JSON';
-- 注意：(provider_id, model_key) 在 V48 已从唯一约束降级为普通索引——同一 provider 下
-- 允许重复 model_key（不同账户/网关），不要「顺手」加回 UNIQUE。

CREATE TABLE IF NOT EXISTS `v5ai_agent` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_key`         VARCHAR(100)  NOT NULL COMMENT 'Agent 对外运行标识（全局唯一；运行时按它解析 Agent）',
    `name`              VARCHAR(200)  NOT NULL COMMENT '名称',
    `description`       TEXT          NULL COMMENT '描述',
    `model_id`          BIGINT        NULL COMMENT '绑定的模型（v5ai_model.id）',
    `status`            VARCHAR(30)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT / PUBLISHED / DISABLED）',
    `published_version` BIGINT        NULL COMMENT '当前生效的已发布版本号（未发布为 null）',
    `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `system_prompt`     TEXT          NULL COMMENT '系统提示词（随发布版本固化）',
    `avatar`            VARCHAR(512)  NULL COMMENT '头像URL',
    `greeting`          TEXT          NULL COMMENT '欢迎语',
    `preset_questions`  TEXT          NULL COMMENT '预设问题列表（JSON数组字符串）',
    `memory_enabled`    TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否启用记忆库',
    `mcp_enabled`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否启用MCP',
    `skill_enabled`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否启用Skill',
    `web_search_enabled` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用联网搜索',
    `rag_enabled`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否启用RAG',
    `rag_call_mode`     SMALLINT      NOT NULL DEFAULT 2 COMMENT 'RAG调用方式: 1=智能调用 2=强制调用',
    `secondary_model_id` BIGINT       NULL COMMENT '次要模型 id（v5ai_model.id，须为 CHAT 类型且已启用）：供会话标题改写与会话摘要压缩调用；为空则回退绑定的对话模型',
    `show_citations`    TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否在聊天窗口展示 RAG 引用折叠块；默认 true；仅影响渲染，引用照常检索与落库（见 docs/adr/0009）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `v5ai_application_agent_key_key` (`agent_key`),
    CONSTRAINT `v5ai_agent_model_id_fkey` FOREIGN KEY (`model_id`) REFERENCES `v5ai_model` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Agent（智能体）：平台核心实体，agent_key 全局唯一；status 为 DRAFT/PUBLISHED/DISABLED；发布时把配置快照写入 v5ai_agent_version';

CREATE TABLE IF NOT EXISTS `v5ai_agent_version` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_key`     VARCHAR(100) NOT NULL COMMENT '所属 Agent 的对外标识（v5ai_agent.agent_key）',
    `version`       BIGINT       NOT NULL COMMENT '版本号（每个 AgentDTO 内自增，从 1 起）',
    `snapshot_json` TEXT         NOT NULL COMMENT '发布时固化的配置快照（JSON 文本）',
    `description`   TEXT         NULL COMMENT '版本描述',
    `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `v5ai_application_version_key_version` (`agent_key`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Agent 发布版本：发布时固化的配置快照（snapshot_json），版本号在每个 Agent 内自增';

-- -----------------------------------------------------------------------------
-- 运行时（会话 / 消息 / 运行）
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `v5ai_conversation` (
    `id`          VARCHAR(36)  NOT NULL COMMENT '会话 ID（UUID 字符串）',
    `agent_key`   VARCHAR(100) NOT NULL COMMENT 'Agent 对外运行标识（跨模块引用，不建外键；删除 Agent 时由 AgentCleanupMapper 显式清理）',
    `user_id`     BIGINT       NULL COMMENT '归属用户（展示与审计冗余，只写不判；归属判定一律以 api_key_id 为准，见 ADR-0006）',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `api_key_id`  BIGINT       NULL COMMENT '会话归属的 API Key（门户侧租户边界）；调试入口产生的会话为 NULL，不属于任何 Key',
    `name`        VARCHAR(100) NULL COMMENT '会话名称：新建会话时由首条提问生成（ConversationNaming），首轮结束后可由模型改写成短标题；用户可改名（name_source=USER 后不再被自动覆盖）',
    `archived_at` DATETIME(3)  NULL COMMENT '归档时间；非空表示已归档：列表默认隐藏且禁止继续对话',
    `name_source` VARCHAR(16)  NULL COMMENT '名称来源：AUTO=系统生成（首条提问兜底名/模型标题，可被模型改写），USER=用户改名（永不被覆盖）',
    PRIMARY KEY (`id`),
    KEY `idx_v5ai_conversation_api_key_agent` (`api_key_id`, `agent_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='门户会话：归属主体是 API Key（api_key_id），user_id 只作展示冗余；调试入口产生的会话该列为 NULL';

CREATE TABLE IF NOT EXISTS `v5ai_message` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversation_id`   VARCHAR(36)  NOT NULL COMMENT '所属会话（v5ai_conversation.id）',
    `agent_key`         VARCHAR(100) NULL COMMENT 'Agent 对外运行标识（跨模块引用，不建外键）',
    `role`              VARCHAR(30)  NOT NULL COMMENT '消息角色（USER / ASSISTANT）',
    `content`           TEXT         NOT NULL COMMENT '消息正文',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `prompt_tokens`     INT          NULL COMMENT '该回答的输入 token：模型回报的真实值优先，未回报时回退为平台字符估算；仅助手消息有值',
    `completion_tokens` INT          NULL COMMENT '该回答的输出 token：口径同上；仅助手消息有值',
    `duration_ms`       INT          NULL COMMENT '该次运行的服务端耗时（毫秒，RUN_STARTED 到收尾）；仅助手消息有值',
    `superseded_at`     DATETIME(3)  NULL COMMENT '作废时间；非空表示该消息已被「重新生成」替换：不参与展示与记忆回放，但数据保留',
    `reasoning`         TEXT         NULL COMMENT '模型的思考过程（仅助手消息有值；NULL=没有思考或 V42 之前的历史数据）；供门户回看，不回放给模型',
    `metadata`          TEXT         NULL COMMENT '消息扩展元数据（JSON 文本）：当前语义为引用快照 {"citations":[...]}，仅助手消息有值；NULL=这一轮没有引用；供门户回看，不回放给模型',
    PRIMARY KEY (`id`),
    -- PG 侧带 WHERE superseded_at IS NULL 的部分索引，MySQL 无对应物 → 退化为普通复合索引
    KEY `idx_v5ai_message_conversation_active` (`conversation_id`, `created_at`, `id`),
    CONSTRAINT `v5ai_message_conversation_id_fkey` FOREIGN KEY (`conversation_id`) REFERENCES `v5ai_conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='会话消息：role 为 USER/ASSISTANT；superseded_at 非空表示已被「重新生成」作废，不参与展示与记忆回放';

CREATE TABLE IF NOT EXISTS `v5ai_message_attachment` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `message_id`  BIGINT       NOT NULL COMMENT '所属消息（v5ai_message.id）',
    `resource_id` BIGINT       NOT NULL COMMENT '资源标识（plm_resource.id，biz_type=ATTACHMENT）',
    `type`        VARCHAR(32)  NOT NULL COMMENT '附件类型，当前只有 IMAGE',
    `ordinal`     INT          NOT NULL DEFAULT 0 COMMENT '同一条消息内附件的展示/回放顺序（从 0 起）',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_v5ai_message_attachment_msg_ord` (`message_id`, `ordinal`),
    KEY `idx_v5ai_message_attachment_resource` (`resource_id`),
    CONSTRAINT `v5ai_message_attachment_message_id_fkey` FOREIGN KEY (`message_id`) REFERENCES `v5ai_message` (`id`) ON DELETE CASCADE,
    CONSTRAINT `v5ai_message_attachment_resource_id_fkey` FOREIGN KEY (`resource_id`) REFERENCES `plm_resource` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='消息携带的附件（图片）：resource_id 指向 plm_resource 中的 ATTACHMENT 资源';

CREATE TABLE IF NOT EXISTS `v5ai_run` (
    `id`              VARCHAR(36)  NOT NULL COMMENT '运行 ID（UUID 字符串）',
    `conversation_id` VARCHAR(36)  NOT NULL COMMENT '所属会话（v5ai_conversation.id）',
    `agent_key`       VARCHAR(100) NOT NULL COMMENT 'Agent 对外运行标识（跨模块引用，不建外键）',
    `status`          VARCHAR(30)  NOT NULL COMMENT '运行状态',
    `error_message`   TEXT         NULL COMMENT '错误信息',
    `started_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
    `completed_at`    DATETIME(3)  NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `v5ai_run_conversation_id_fkey` FOREIGN KEY (`conversation_id`) REFERENCES `v5ai_conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='一次 Agent 运行：id 为 UUID 字符串，status 记录运行终态';

CREATE TABLE IF NOT EXISTS `v5ai_run_event` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id`     VARCHAR(36)  NOT NULL COMMENT '所属运行（v5ai_run.id）',
    `event_type` VARCHAR(50)  NOT NULL COMMENT '事件类型（RuntimeEventType 枚举名：RUN_STARTED / MODEL_CALL / TEXT_DELTA / TOOL_CALL / TOOL_RESULT / RETRIEVAL / MESSAGE_COMPLETED / RUN_COMPLETED / RUN_FAILED / PERMISSION_REQUIRED）',
    `payload`    TEXT         NULL COMMENT '事件负载（JSON 文本；RUN_STARTED 为提问消息 id + 上下文窗口统计，RUN_COMPLETED 为本次用量汇总）',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `v5ai_run_event_run_id_fkey` FOREIGN KEY (`run_id`) REFERENCES `v5ai_run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='运行事件流水：SSE 事件的落库副本（不含思考过程，也不含内部用量事件 MODEL_USAGE）';

CREATE TABLE IF NOT EXISTS `v5ai_agent_state` (
    `conversation_id` VARCHAR(36) NOT NULL COMMENT '所属会话（v5ai_conversation.id）；一会话一行',
    `state_json`      TEXT        NOT NULL COMMENT 'Agent 运行状态的 JSON 序列化结果（按会话滚动覆盖）',
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`conversation_id`),
    CONSTRAINT `v5ai_agent_state_conversation_id_fkey` FOREIGN KEY (`conversation_id`) REFERENCES `v5ai_conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Agent 运行状态：按会话保存的运行态 JSON，一会话一行';

CREATE TABLE IF NOT EXISTS `v5ai_conversation_summary` (
    `conversation_id`         VARCHAR(36) NOT NULL COMMENT '所属会话（v5ai_conversation.id）；一会话一行',
    `summary`                 TEXT        NOT NULL COMMENT '摘要正文（模型生成，长度上限见 v5ai.chat.conversation-summary.max-summary-chars）',
    `covered_until_message_id` BIGINT     NOT NULL COMMENT '摘要覆盖水位：id ≤ 该值的消息都已并入摘要（重新生成锚点落在其中时整条摘要作废）',
    `covered_messages`        INT         NOT NULL DEFAULT 0 COMMENT '已并入摘要的消息条数（观测用）',
    `model_id`                BIGINT      NULL COMMENT '生成该摘要的模型 id（审计用；配置了固定摘要模型时与 Agent 绑定模型不同）',
    `created_at`              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='会话历史摘要：历史窗口之外的旧内容压缩成一段文本继续参与上下文；一会话一行（滚动覆盖）';

-- -----------------------------------------------------------------------------
-- API Key / 配额 / 用量
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `v5ai_api_keys` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `secret_hash`  VARCHAR(255) NOT NULL COMMENT 'API Key 密文（BCrypt）',
    `enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `user_id`      BIGINT       NOT NULL COMMENT 'Key 归属用户（plm_user.id）',
    `name`         VARCHAR(100) NOT NULL COMMENT 'Key 名称（页面主标识，同一用户内唯一）',
    `tracking_id`  VARCHAR(36)  NOT NULL COMMENT '跟踪 ID：独立 UUID，与明文 Key 无关联，仅供展示与日志/审计对账',
    `last_used_at` DATETIME(3)  NULL COMMENT '最新使用时间（鉴权成功时刷新）',
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    `key_hash`     VARCHAR(64)  NOT NULL COMMENT '明文 Key 的 SHA-256 摘要（小写十六进制），运行时唯一索引定位密钥行',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_v5ai_api_keys_tracking_id` (`tracking_id`),
    UNIQUE KEY `uk_v5ai_api_keys_key_hash` (`key_hash`),
    UNIQUE KEY `uk_v5ai_api_keys_user_name` (`user_id`, `name`),
    KEY `idx_v5ai_api_keys_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Agent API Key：归属创建用户，可访问的 Agent 由 v5ai_api_keys_agent 绑定；库内只存摘要与密文（key_hash sha256 定位 / secret_hash BCrypt 校验）';

CREATE TABLE IF NOT EXISTS `v5ai_api_keys_agent` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `api_key_id`  BIGINT       NOT NULL COMMENT 'v5ai_api_keys.id（Key 删除时级联删除绑定）',
    `agent_key`   VARCHAR(100) NOT NULL COMMENT 'v5ai_agent.agent_key（不建跨模块外键，删除 Agent 时显式清理）',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定创建时间（由数据库默认值 CURRENT_TIMESTAMP 填充，写入侧不赋值）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_v5ai_api_keys_agent` (`api_key_id`, `agent_key`),
    KEY `idx_v5ai_api_keys_agent_agent_key` (`agent_key`),
    CONSTRAINT `v5ai_api_keys_agent_api_key_id_fkey` FOREIGN KEY (`api_key_id`) REFERENCES `v5ai_api_keys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='API Key ↔ Agent 绑定：某把 Key 可以访问哪些 Agent（仅已发布 Agent）';

CREATE TABLE IF NOT EXISTS `v5ai_app_quota` (
    `agent_key`         VARCHAR(100) NOT NULL COMMENT 'Agent 对外运行标识（配额按 Agent 维度配置，本表主键）',
    `daily_model_calls` INT          NOT NULL DEFAULT 0 COMMENT '每日模型调用次数上限；0 = 不限制',
    `daily_tokens`      BIGINT       NOT NULL DEFAULT 0 COMMENT '每日 token 上限；0 = 不限制；当前未参与拦截（checkAllowed 只校验每分钟限流与每日调用次数）',
    `rate_per_minute`   INT          NOT NULL DEFAULT 0 COMMENT '每分钟调用次数上限（进程内限流器）；0 = 不限制',
    `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`agent_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='按 Agent 维度的调用配额：每日调用次数、每日 token、每分钟限流；0 一律表示不限制';

CREATE TABLE IF NOT EXISTS `v5ai_app_usage` (
    `agent_key`   VARCHAR(100) NOT NULL COMMENT 'Agent 对外运行标识（复合主键之一）',
    `usage_date`  DATE         NOT NULL COMMENT '统计自然日（按服务端 LocalDate.now() 切分；复合主键之一）',
    `model_calls` INT          NOT NULL DEFAULT 0 COMMENT '当日模型调用次数（每次调用 upsert 累加 1）',
    `tokens`      BIGINT       NOT NULL DEFAULT 0 COMMENT '当日累计 token（每次调用累加，负数按 0 计）',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`agent_key`, `usage_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='按 Agent 的每日用量累计：复合主键 (agent_key, usage_date)，由 upsert 累加；勿使用 updateById/deleteById';

CREATE TABLE IF NOT EXISTS `v5ai_model_usage` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id`           VARCHAR(64)  NULL COMMENT '关联运行（v5ai_run.id；非运行期调用为 NULL）',
    `agent_key`        VARCHAR(100) NULL COMMENT 'Agent 对外运行标识（跨模块引用，不建外键）',
    `model_id`         BIGINT       NULL COMMENT '平台模型配置 ID；历史记录可能为空，用于用量聚合区分重复 model_key',
    `model_key`        VARCHAR(200) NULL COMMENT '被调用模型标识',
    `prompt_tokens`    BIGINT       NOT NULL DEFAULT 0 COMMENT '输入 token（真实用量优先，服务端未回报时按平台规则估算）',
    `completion_tokens` BIGINT      NOT NULL DEFAULT 0 COMMENT '输出 token（口径同 prompt_tokens）',
    `total_tokens`     BIGINT       NOT NULL DEFAULT 0 COMMENT '总 token（输入 + 输出）',
    `duration_ms`      BIGINT       NULL COMMENT '本次调用耗时（毫秒）',
    `status`           VARCHAR(30)  NOT NULL DEFAULT 'SUCCESS' COMMENT '调用结果状态（默认 SUCCESS）',
    `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_v5ai_model_usage_app` (`agent_key`, `created_at`),
    KEY `idx_v5ai_model_usage_created` (`created_at`),
    KEY `idx_v5ai_model_usage_model` (`model_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='模型用量明细账（可观测性统计）：每次模型调用一行；服务端未回报用量时按平台规则估算';

-- -----------------------------------------------------------------------------
-- RAG：存储实例 / 知识库 / 文档 / 切片 / 任务
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `v5ai_store_instance` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(128)  NOT NULL COMMENT '实例名称',
    `category`    SMALLINT      NOT NULL COMMENT '分类: 1-向量库 2-搜索引擎',
    `type`        SMALLINT      NOT NULL COMMENT '类型: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-PG_FULLTEXT',
    `config`      TEXT          NULL COMMENT '连接参数 JSON',
    `status`      SMALLINT      NULL DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
    `is_default`  TINYINT(1)    NULL DEFAULT 0 COMMENT '是否为该 category 下默认实例',
    `created_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `description` VARCHAR(1000) NULL COMMENT '实例描述',
    PRIMARY KEY (`id`),
    KEY `idx_store_instance_category` (`category`),
    KEY `idx_store_instance_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='存储实例';

CREATE TABLE IF NOT EXISTS `v5ai_knowledge_base` (
    `id`                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`                      VARCHAR(200) NOT NULL COMMENT '知识库名称',
    `description`               TEXT         NULL COMMENT '知识库描述',
    `status`                    VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE' COMMENT '启用状态（ACTIVE / DISABLED）',
    `created_at`                DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`                DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `icon`                      VARCHAR(512) NULL COMMENT '图标',
    `embedding_model_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '向量模型id',
    `vector_store_instance_id`  BIGINT       NULL COMMENT '向量存储实例id',
    `dimension_of_vector_model` INT          NOT NULL DEFAULT 0 COMMENT '向量维度',
    `rerank_model_id`           BIGINT       NULL COMMENT '重排序模型id',
    `search_engine_enable`      TINYINT(1)   NULL DEFAULT 0 COMMENT '搜索引擎启用标志',
    `search_engine_instance_id` BIGINT       NULL COMMENT '搜索引擎实例id',
    `delimiter`                 VARCHAR(32)  NULL DEFAULT '\n\n' COMMENT '文档分割符',
    `rag_enhancement`           TEXT         NULL COMMENT 'RAG增强配置',
    `config`                    TEXT         NULL COMMENT 'RAG检索和问答的页面配置参数（RagConfigDO JSON）',
    `dedup_strategy`            SMALLINT     NOT NULL DEFAULT 2 COMMENT '去重策略: 0=NONE 1=BY_NAME 2=BY_CONTENT 3=BY_NAME_OR_CONTENT',
    `dedup_action`              SMALLINT     NOT NULL DEFAULT 0 COMMENT '冲突动作: 0=REJECT 1=SKIP 2=OVERWRITE',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='知识库：status 为 ACTIVE/DISABLED';

-- 建在知识库之后：PG 侧本表的 knowledge_base_id 带外键（agent_key 不带，跨模块引用）
CREATE TABLE IF NOT EXISTS `v5ai_agent_knowledge` (
    `agent_key`         VARCHAR(100) NOT NULL COMMENT 'Agent 对外运行标识（跨模块引用，不建外键）',
    `knowledge_base_id` BIGINT       NOT NULL COMMENT '绑定的知识库 ID',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定创建时间',
    PRIMARY KEY (`agent_key`, `knowledge_base_id`),
    CONSTRAINT `v5ai_agent_knowledge_knowledge_base_id_fkey` FOREIGN KEY (`knowledge_base_id`) REFERENCES `v5ai_knowledge_base` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Agent ↔ 知识库绑定：复合主键 (agent_key, knowledge_base_id)，无 updated_at 列';

CREATE TABLE IF NOT EXISTS `v5ai_knowledge_document` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `knowledge_base_id` BIGINT        NOT NULL COMMENT '所属知识库（v5ai_knowledge_base.id）',
    `title`             VARCHAR(300)  NOT NULL COMMENT '文档标题',
    `file_type`         VARCHAR(30)   NOT NULL COMMENT '文件类型（TXT / MARKDOWN / PDF / DOCX / URL）',
    `source_type`       VARCHAR(30)   NOT NULL DEFAULT 'UPLOAD' COMMENT '来源类型: UPLOAD=上传 URL=网络',
    `status`            SMALLINT      NOT NULL DEFAULT 0 COMMENT '索引状态: 0=待处理 1=解析中 2=处理中 3=处理完成 4=处理失败',
    `content`           LONGBLOB      NULL COMMENT '原始内容（BYTEA）',
    `parsed_text`       TEXT          NULL COMMENT '解析后的纯文本',
    `error_message`     TEXT          NULL COMMENT '失败时的错误信息',
    `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `storage_type`      VARCHAR(32)   NOT NULL DEFAULT 'LOCAL' COMMENT '存储类型: LOCAL=本地 MINIO=minio',
    `storage_path`      VARCHAR(1024) NULL COMMENT '存储路径',
    `file_size`         BIGINT        NOT NULL DEFAULT 0 COMMENT '文件大小(bytes)',
    `chunk_count`       INT           NOT NULL DEFAULT 0 COMMENT '分片数量',
    `parse_time`        INT           NOT NULL DEFAULT 0 COMMENT '解析耗时（毫秒）',
    `content_hash`      VARCHAR(64)   NULL COMMENT '文件内容SHA-256哈希，用于去重',
    `resource_id`       BIGINT        NULL COMMENT '关联资源库 plm_resource.id',
    `parse_engine`      VARCHAR(32)   NULL COMMENT '实际使用的文档解析引擎，外部服务失败时为 default',
    `parse_diagnostics` TEXT          NULL COMMENT '文档级解析结构化结果或诊断原文',
    PRIMARY KEY (`id`),
    CONSTRAINT `v5ai_knowledge_document_knowledge_base_id_fkey` FOREIGN KEY (`knowledge_base_id`) REFERENCES `v5ai_knowledge_base` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='知识库文档：content 存原始内容（BYTEA）、parsed_text 为解析后纯文本；status 为索引状态（0 待处理 / 1 解析中 / 2 处理中 / 3 完成 / 4 失败）';

CREATE TABLE IF NOT EXISTS `v5ai_knowledge_chunk` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `knowledge_base_id` BIGINT       NOT NULL COMMENT '所属知识库（v5ai_knowledge_base.id）',
    `document_id`       BIGINT       NOT NULL COMMENT '所属文档（v5ai_knowledge_document.id）',
    `chunk_index`       INT          NOT NULL COMMENT '文档内分片序号（从 0 起）',
    `content`           TEXT         NOT NULL COMMENT '分片正文',
    `metadata`          JSON         NULL COMMENT '分片元数据 JSON：{"documentId","chunkIndex","title"}；由索引 Worker 写入，缺省 {}',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `paragraph_index`   INT          NULL COMMENT '段落索引',
    `token_count`       INT          NULL COMMENT '分片token数量',
    `vector_id`         VARCHAR(128) NULL COMMENT '向量id',
    `content_hash`      VARCHAR(64)  NULL COMMENT 'chunk内容SHA-256，用于向量去重',
    `source_type`       VARCHAR(20)  NOT NULL DEFAULT 'TEXT' COMMENT 'chunk来源类型: TEXT=文本 IMAGE=图片',
    `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `keyword_tokens`    JSON         NULL COMMENT '关键词分词（jieba INDEX 模式，保留重复词；JSON 字符串数组，对应 PG 的 text[]）；仅服务关键词路 BM25：词频 tf 由数组内出现次数现算、文档长度 dl = 数组长度、df/avgdl 检索时按库集合现算；写入由 Worker 重建与手工切片增改维护',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_chunk_vector_id` (`vector_id`),
    KEY `idx_v5ai_knowledge_chunk_document` (`document_id`),
    KEY `idx_knowledge_chunk_rag` (`knowledge_base_id`),
    KEY `idx_chunk_knowledge_hash` (`knowledge_base_id`, `content_hash`),
    KEY `idx_chunk_knowledge_source_type` (`knowledge_base_id`, `source_type`),
    CONSTRAINT `v5ai_knowledge_chunk_document_id_fkey` FOREIGN KEY (`document_id`) REFERENCES `v5ai_knowledge_document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='知识库分片：检索单元；正文在 content、向量在向量库（vector_id）、关键词分词在 keyword_tokens';
-- PG 侧 keyword_tokens 是 text[] + GIN 索引；MySQL 用 JSON 数组 + 多值函数索引支撑
-- JSON_OVERLAPS / JSON_CONTAINS 召回（M3 的关键词检索方言分支依赖它）。
CREATE INDEX idx_chunk_keyword_tokens ON v5ai_knowledge_chunk ((CAST(`keyword_tokens` AS CHAR(64) ARRAY)));

CREATE TABLE IF NOT EXISTS `v5ai_knowledge_task` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `knowledge_base_id` BIGINT       NOT NULL COMMENT '所属知识库（v5ai_knowledge_base.id）',
    `document_id`       BIGINT       NOT NULL COMMENT '关联文档（v5ai_knowledge_document.id）',
    `task_type`         VARCHAR(30)  NOT NULL DEFAULT 'PARSE_AND_INDEX' COMMENT '任务类型（如 PARSE_AND_INDEX）',
    `status`            VARCHAR(30)  NOT NULL DEFAULT 'PENDING' COMMENT '任务状态（PENDING / PROCESSING / COMPLETED / FAILED）',
    `attempt_count`     INT          NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    `max_attempts`      INT          NOT NULL DEFAULT 3 COMMENT '最大尝试次数',
    `error_message`     TEXT         NULL COMMENT '失败时的错误信息',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_v5ai_knowledge_task_status` (`status`, `created_at`),
    -- PG 侧只有 document_id 建了外键，knowledge_base_id 没有 → 保持一致
    CONSTRAINT `v5ai_knowledge_task_document_id_fkey` FOREIGN KEY (`document_id`) REFERENCES `v5ai_knowledge_document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='知识库索引任务：由 Worker 异步消费；status 为 PENDING/PROCESSING/COMPLETED/FAILED，带重试计数';

-- -----------------------------------------------------------------------------
-- MCP / Skill
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `v5ai_mcp_server` (
    `id`                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`               VARCHAR(200)   NOT NULL COMMENT '平台内唯一标识名（也是 MCP 客户端名）',
    `transport_type`     VARCHAR(30)    NOT NULL COMMENT '传输类型（STREAMABLE_HTTP / SSE / STDIO）',
    `endpoint`           VARCHAR(1000)  NULL COMMENT 'HTTP 传输为 URL；Stdio 传输为可执行命令',
    `args_json`          TEXT           NULL COMMENT 'Stdio 命令参数（JSON 文本）',
    `headers_ciphertext` TEXT           NULL COMMENT 'HTTP 请求头密文（明文 JSON 加密后落库）',
    `env_ciphertext`     TEXT           NULL COMMENT 'Stdio 环境变量密文（明文 JSON 加密后落库）',
    `timeout_seconds`    INT            NOT NULL DEFAULT 30 COMMENT '请求超时秒数',
    `status`             VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE' COMMENT '启用状态（ACTIVE / DISABLED）',
    `last_test_status`   VARCHAR(30)    NULL COMMENT '最近一次连接测试结果（ok/failed，可为 null）',
    `last_test_message`  TEXT           NULL COMMENT '最近一次连接测试消息（可为 null）',
    `last_tested_at`     DATETIME(3)    NULL COMMENT '最近一次连接测试时间（可为 null）',
    `created_at`         DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`         DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`)
    -- 注意：PG 侧 name 无唯一约束（「平台内唯一」由服务层校验），这里不加 UNIQUE 以保持等价
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='MCP Server 注册：Streamable HTTP / SSE / Stdio；headers/env 为密文列';

CREATE TABLE IF NOT EXISTS `v5ai_agent_mcp` (
    `agent_key`     VARCHAR(100) NOT NULL COMMENT 'Agent 对外运行标识（跨模块引用，不建外键）',
    `mcp_server_id` BIGINT       NOT NULL COMMENT '绑定的 MCP Server',
    `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定创建时间',
    PRIMARY KEY (`agent_key`, `mcp_server_id`),
    CONSTRAINT `v5ai_agent_mcp_mcp_server_id_fkey` FOREIGN KEY (`mcp_server_id`) REFERENCES `v5ai_mcp_server` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Agent ↔ MCP Server 绑定：复合主键 (agent_key, mcp_server_id)';

CREATE TABLE IF NOT EXISTS `v5ai_mcp_tool` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `server_id`          BIGINT       NOT NULL COMMENT '所属 MCP Server',
    `tool_name`          VARCHAR(300) NOT NULL COMMENT '工具名（MCP 协议内唯一，与 serverId 组成唯一键）',
    `description`        TEXT         NULL COMMENT '工具描述',
    `input_schema_json`  TEXT         NULL COMMENT '输入 JSON Schema（JSON 文本）',
    `read_only`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '服务器声明的只读提示',
    `permission`         VARCHAR(30)  NOT NULL DEFAULT 'ALLOW' COMMENT '平台权限决策（ALLOW / APPROVE / DENY）',
    `last_discovered_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次发现时间',
    `created_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `v5ai_mcp_tool_server_id_tool_name` (`server_id`, `tool_name`),
    KEY `idx_v5ai_mcp_tool_server` (`server_id`),
    CONSTRAINT `v5ai_mcp_tool_server_id_fkey` FOREIGN KEY (`server_id`) REFERENCES `v5ai_mcp_server` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='MCP Tool 缓存：来自 Tool 发现结果；permission 为平台权限决策（ALLOW / APPROVE / DENY）';

CREATE TABLE IF NOT EXISTS `v5ai_mcp_tool_call` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id`            VARCHAR(64)  NOT NULL COMMENT '所属运行 ID',
    `server_id`         BIGINT       NOT NULL COMMENT '所属 MCP Server',
    `server_name`       VARCHAR(200) NOT NULL COMMENT 'MCP Server 名称（冗余存储，便于审计查询）',
    `tool_name`         VARCHAR(300) NOT NULL COMMENT '工具名',
    `arguments_summary` TEXT         NULL COMMENT '参数摘要（截断，不落完整敏感参数）',
    `decision`          VARCHAR(30)  NOT NULL COMMENT '平台权限决策（ALLOW / APPROVE / DENY）',
    `status`            VARCHAR(30)  NOT NULL COMMENT '调用结果状态',
    `duration_ms`       BIGINT       NULL COMMENT '调用耗时（毫秒）',
    `message`           TEXT         NULL COMMENT '错误信息（失败时）',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_v5ai_mcp_tool_call_run` (`run_id`),
    KEY `idx_v5ai_mcp_tool_call_server` (`server_id`),
    CONSTRAINT `v5ai_mcp_tool_call_server_id_fkey` FOREIGN KEY (`server_id`) REFERENCES `v5ai_mcp_server` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='MCP Tool 调用审计：每次工具调用落库，用于追溯与合规';

-- -----------------------------------------------------------------------------
-- Skill
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `v5ai_skill` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`               VARCHAR(200) NOT NULL COMMENT '技能名（来自 SKILL.md frontmatter，全局唯一）',
    `description`        TEXT         NULL COMMENT '技能描述',
    `status`             VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE' COMMENT '启用状态（ACTIVE / DISABLED）',
    `current_version_id` BIGINT       NULL COMMENT '当前生效（注入运行时）的已发布版本 ID',
    `created_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `v5ai_skill_name_key` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Skill：技能名全局唯一（取自 SKILL.md frontmatter），status 为 ACTIVE/DISABLED';

CREATE TABLE IF NOT EXISTS `v5ai_skill_version` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `skill_id`     BIGINT       NOT NULL COMMENT '所属 Skill',
    `version`      BIGINT       NOT NULL COMMENT '版本号（每个 Skill 内自增）',
    `status`       VARCHAR(30)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT / PUBLISHED / OFFLINE）',
    `description`  TEXT         NULL COMMENT '版本描述',
    `published_at` DATETIME(3)  NULL COMMENT '发布时间（未发布为 null）',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `v5ai_skill_version_skill_id_version` (`skill_id`, `version`),
    KEY `idx_v5ai_skill_version_skill` (`skill_id`),
    CONSTRAINT `v5ai_skill_version_skill_id_fkey` FOREIGN KEY (`skill_id`) REFERENCES `v5ai_skill` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Skill 版本：DRAFT/PUBLISHED/OFFLINE；current_version_id 指向运行时注入的版本';

CREATE TABLE IF NOT EXISTS `v5ai_agent_skill` (
    `agent_key`  VARCHAR(100) NOT NULL COMMENT 'Agent 对外运行标识（跨模块引用，不建外键）',
    `skill_id`   BIGINT       NOT NULL COMMENT '绑定的 Skill',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定创建时间',
    PRIMARY KEY (`agent_key`, `skill_id`),
    CONSTRAINT `v5ai_agent_skill_skill_id_fkey` FOREIGN KEY (`skill_id`) REFERENCES `v5ai_skill` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Agent ↔ Skill 绑定：复合主键 (agent_key, skill_id)，无自增主键列';

CREATE TABLE IF NOT EXISTS `v5ai_skill_file` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `skill_id`   BIGINT       NOT NULL COMMENT '所属 Skill',
    `version_id` BIGINT       NOT NULL COMMENT '所属版本',
    `file_path`  VARCHAR(500) NOT NULL COMMENT '包内相对路径（如 "SKILL.md"、"prompts/guide.md"）',
    `content`    TEXT         NOT NULL COMMENT '文本内容（UTF-8）',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `v5ai_skill_file_version_id_file_path` (`version_id`, `file_path`),
    KEY `idx_v5ai_skill_file_version` (`version_id`),
    CONSTRAINT `v5ai_skill_file_skill_id_fkey` FOREIGN KEY (`skill_id`) REFERENCES `v5ai_skill` (`id`),
    CONSTRAINT `v5ai_skill_file_version_id_fkey` FOREIGN KEY (`version_id`) REFERENCES `v5ai_skill_version` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Skill 包文件：发布版本的原始文件（SKILL.md、prompts/、resources/ 等），运行时据此注入 Workspace';

-- -----------------------------------------------------------------------------
-- Workflow
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `v5ai_workflow` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_key`         VARCHAR(100) NOT NULL COMMENT '对外标识（全局唯一）',
    `name`                 VARCHAR(200) NOT NULL COMMENT '名称',
    `description`          TEXT         NULL COMMENT '描述',
    `status`               VARCHAR(30)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT / PUBLISHED / DISABLED）',
    `draft_definition`     TEXT         NULL COMMENT '草稿定义 JSON（{ nodes, edges }）',
    `published_definition` TEXT         NULL COMMENT '已发布定义 JSON（{ nodes, edges }）',
    `published_version`    BIGINT       NULL COMMENT '已发布版本号',
    `published_at`         DATETIME(3)  NULL COMMENT '发布时间',
    `created_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `v5ai_workflow_workflow_key_key` (`workflow_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Workflow：草稿与已发布定义分别以 JSON 文本存储';

CREATE TABLE IF NOT EXISTS `v5ai_workflow_run` (
    `run_id`           VARCHAR(36)  NOT NULL COMMENT '运行 ID（VARCHAR 主键，UUID 字符串）',
    `workflow_key`     VARCHAR(100) NOT NULL COMMENT '被运行的 Workflow 对外标识（v5ai_workflow.workflow_key）',
    `workflow_version` BIGINT       NULL COMMENT '运行时锁定的已发布版本号（v5ai_workflow.published_version）',
    `status`           VARCHAR(30)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态（RUNNING / SUCCEEDED / FAILED）',
    `inputs`           TEXT         NULL COMMENT '运行输入 JSON',
    `outputs`          TEXT         NULL COMMENT '运行输出 JSON',
    `error`            TEXT         NULL COMMENT '失败信息（成功为 NULL）',
    `started_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '运行开始时间',
    `finished_at`      DATETIME(3)  NULL COMMENT '运行结束时间（未结束为 NULL）',
    `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`run_id`),
    KEY `idx_v5ai_workflow_run_key` (`workflow_key`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Workflow 运行：run_id 为 VARCHAR 主键，锁定运行时的已发布版本号';

CREATE TABLE IF NOT EXISTS `v5ai_workflow_node_run` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id`      VARCHAR(36)  NOT NULL COMMENT '所属 Workflow 运行（v5ai_workflow_run.run_id）',
    `node_id`     VARCHAR(100) NOT NULL COMMENT '流程定义中的节点 ID（definition JSON 内的节点 key，非自增列）',
    `node_type`   VARCHAR(30)  NOT NULL COMMENT '节点类型（START / AGENT / CONDITION / END）',
    `status`      VARCHAR(30)  NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING / RUNNING / SUCCEEDED / FAILED / SKIPPED）',
    `inputs`      TEXT         NULL COMMENT '节点输入 JSON',
    `outputs`     TEXT         NULL COMMENT '节点输出 JSON',
    `error`       TEXT         NULL COMMENT '失败信息（成功为 NULL）',
    `started_at`  DATETIME(3)  NULL COMMENT '节点开始执行时间',
    `finished_at` DATETIME(3)  NULL COMMENT '节点结束时间（未结束为 NULL）',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_v5ai_workflow_node_run_run` (`run_id`),
    CONSTRAINT `v5ai_workflow_node_run_run_id_fkey` FOREIGN KEY (`run_id`) REFERENCES `v5ai_workflow_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='Workflow 节点运行：每次节点执行的输入输出与耗时，无 updated_at 列';
