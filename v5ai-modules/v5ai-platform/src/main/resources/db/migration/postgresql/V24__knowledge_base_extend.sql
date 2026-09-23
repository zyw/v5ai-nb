-- ============================================================
-- V24-1：v5ai_knowledge_base 扩展（RAG 创建/编辑配置）
-- 说明：
--   1) 按「创建 RAG」表单新增 12 列：嵌入模型/向量库实例/维度/
--      重排模型/混合检索/搜索引擎实例/分隔符/RAG增强/页面配置/
--      去重策略/冲突动作/图标；
--   2) NOT NULL 列（embedding_model_id / dimension_of_vector_model）
--      以 DEFAULT 0 兼容存量行，应用层创建/编辑时校验必须 > 0；
--   3) config 列存 RagConfigDO 序列化 JSON（检索/切片/解析参数）。
-- ============================================================

alter table v5ai_knowledge_base
    add column if not exists icon varchar(512),
    add column if not exists embedding_model_id bigint not null default 0,
    add column if not exists vector_store_instance_id bigint,
    add column if not exists dimension_of_vector_model int not null default 0,
    add column if not exists rerank_model_id bigint,
    add column if not exists search_engine_enable boolean default false,
    add column if not exists search_engine_instance_id bigint,
    add column if not exists delimiter varchar(32) default E'\n\n',
    add column if not exists rag_enhancement text,
    add column if not exists config text,
    add column if not exists dedup_strategy smallint not null default 2,
    add column if not exists dedup_action smallint not null default 0;

comment on column v5ai_knowledge_base.icon is '图标';
comment on column v5ai_knowledge_base.embedding_model_id is '向量模型id';
comment on column v5ai_knowledge_base.vector_store_instance_id is '向量存储实例id';
comment on column v5ai_knowledge_base.dimension_of_vector_model is '向量维度';
comment on column v5ai_knowledge_base.rerank_model_id is '重排序模型id';
comment on column v5ai_knowledge_base.search_engine_enable is '搜索引擎启用标志';
comment on column v5ai_knowledge_base.search_engine_instance_id is '搜索引擎实例id';
comment on column v5ai_knowledge_base.delimiter is '文档分割符';
comment on column v5ai_knowledge_base.rag_enhancement is 'RAG增强配置';
comment on column v5ai_knowledge_base.config is 'RAG检索和问答的页面配置参数（RagConfigDO JSON）';
comment on column v5ai_knowledge_base.dedup_strategy is '去重策略: 0=NONE 1=BY_NAME 2=BY_CONTENT 3=BY_NAME_OR_CONTENT';
comment on column v5ai_knowledge_base.dedup_action is '冲突动作: 0=REJECT 1=SKIP 2=OVERWRITE';


-- ---------- 1. 通用资源存储表 ----------

create table if not exists plm_resource
(
    id            BIGSERIAL PRIMARY KEY,
    storage_key   VARCHAR(512) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_size     BIGINT       DEFAULT 0,
    mime_type     VARCHAR(128),
    storage_type  VARCHAR(32)  NOT NULL DEFAULT 'LOCAL',
    access_url    VARCHAR(1024),
    biz_type      VARCHAR(64)  NOT NULL DEFAULT 'GENERAL',
    biz_id        BIGINT,
    created_by    BIGINT,
    create_dt     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_dt     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_storage_key UNIQUE (storage_key)
    );

comment on table plm_resource is '通用资源存储';
comment on column plm_resource.storage_key is '存储键（相对路径或对象Key）';
comment on column plm_resource.original_name is '原始文件名';
comment on column plm_resource.file_size is '文件大小(bytes)';
comment on column plm_resource.mime_type is 'MIME类型';
comment on column plm_resource.storage_type is '存储类型: LOCAL/MINIO';
comment on column plm_resource.access_url is '访问URL';
comment on column plm_resource.biz_type is '业务类型: AVATAR/ATTACHMENT/DOCUMENT/GENERAL';
comment on column plm_resource.biz_id is '关联业务ID';
comment on column plm_resource.created_by is '创建者ID';
comment on column plm_resource.create_dt is '创建时间';
comment on column plm_resource.update_dt is '更新时间';

create index if not exists idx_plm_resource_original_name on plm_resource (original_name);
create index if not exists idx_plm_resource_biz_type on plm_resource (biz_type);
create index if not exists idx_plm_resource_create_dt on plm_resource (create_dt);

-- ============================================================
-- V24-2：通用资源存储（plm_resource）+ 「资源存储」菜单
-- 说明：
--   1) 通用资源存储表：上传文件元数据（storage_key/original_name/
--      file_size/mime_type/storage_type/access_url/biz_type/biz_id），
--      LOCAL 为默认存储类型，MINIO 可选；created_by 记录创建者；
--      create_dt/update_dt 由应用层填充（无触发器）。
--   2) 在「资源」目录下新增「资源存储」菜单（含按钮权限）并授权给
--      ADMIN；幂等：建表与菜单/授权均以防重写法实现。
-- ============================================================

-- ---------- 2. 「资源存储」菜单（资源组，order_num=18） ----------

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储', id, 18, 'resources', 'resource/resources/index', 'C', '0', '0', 'platform:resource:list', 'files'
from plm_menu
where menu_name = '资源'
  and not exists (select 1 from plm_menu where menu_name = '资源存储');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储查询', id, 1, '', '', 'F', '0', '0', 'platform:resource:query', '#'
from plm_menu
where menu_name = '资源存储'
  and not exists (select 1 from plm_menu where perms = 'platform:resource:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储上传', id, 2, '', '', 'F', '0', '0', 'platform:resource:add', '#'
from plm_menu
where menu_name = '资源存储'
  and not exists (select 1 from plm_menu where perms = 'platform:resource:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储修改', id, 3, '', '', 'F', '0', '0', 'platform:resource:edit', '#'
from plm_menu
where menu_name = '资源存储'
  and not exists (select 1 from plm_menu where perms = 'platform:resource:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储删除', id, 4, '', '', 'F', '0', '0', 'platform:resource:remove', '#'
from plm_menu
where menu_name = '资源存储'
  and not exists (select 1 from plm_menu where perms = 'platform:resource:remove');

-- ---------- 3. 新菜单授权给 ADMIN（幂等） ----------

insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and m.menu_name in ('资源存储', '资源存储查询', '资源存储上传', '资源存储修改', '资源存储删除')
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);


-- ============================================================
-- V24-3：v5ai_knowledge_document 扩展
-- 说明：
--   1) source 列重命名为 source_type（UPLOAD=上传 / URL=网络），
--      旧数据以 http(s):// 开头的按 URL 归类，其余按 UPLOAD 归类；
--   2) status 由 VARCHAR 改为 SMALLINT（0-待处理 1-解析中 2-处理中
--      3-处理完成 4-处理失败），旧状态 PENDING->0 PROCESSING->2
--      COMPLETED->3 FAILED->4；
--   3) 新增存储元数据列（storage_type/storage_path/file_size/
--      chunk_count/parse_time/content_hash）与资源库关联列
--      resource_id（plm_resource.id）。
-- ============================================================

-- ---------- 1. source -> source_type ----------

alter table v5ai_knowledge_document
    rename column source to source_type;

alter table v5ai_knowledge_document
alter column source_type type varchar(30),
    alter column source_type set default 'UPLOAD',
    alter column source_type set not null;

update v5ai_knowledge_document
set source_type = case
                      when source_type like 'http://%' or source_type like 'https://%' then 'URL'
                      else 'UPLOAD'
    end;

-- ---------- 2. status 改为 SMALLINT ----------

-- 先移除旧默认值（'PENDING' 无法自动转 smallint，否则报 42804：
-- "default for column status cannot be cast automatically to type smallint"），
-- 改完类型后再重新设置 smallint 默认值。
alter table v5ai_knowledge_document
    alter column status drop default;

alter table v5ai_knowledge_document
alter column status type smallint using (
        case status
            when 'PENDING' then 0
            when 'PROCESSING' then 2
            when 'COMPLETED' then 3
            when 'FAILED' then 4
            else 0
        end),
    alter column status set default 0,
    alter column status set not null;

-- ---------- 3. 新增列 ----------

alter table v5ai_knowledge_document
    add column if not exists storage_type varchar(32) not null default 'LOCAL',
    add column if not exists storage_path varchar(1024),
    add column if not exists file_size bigint not null default 0,
    add column if not exists chunk_count int not null default 0,
    add column if not exists parse_time int not null default 0,
    add column if not exists content_hash varchar(64),
    add column if not exists resource_id bigint;

comment on column v5ai_knowledge_document.source_type is '来源类型: UPLOAD=上传 URL=网络';
comment on column v5ai_knowledge_document.status is '索引状态: 0=待处理 1=解析中 2=处理中 3=处理完成 4=处理失败';
comment on column v5ai_knowledge_document.storage_type is '存储类型: LOCAL=本地 MINIO=minio';
comment on column v5ai_knowledge_document.storage_path is '存储路径';
comment on column v5ai_knowledge_document.file_size is '文件大小(bytes)';
comment on column v5ai_knowledge_document.chunk_count is '分片数量';
comment on column v5ai_knowledge_document.parse_time is '解析耗时（毫秒）';
comment on column v5ai_knowledge_document.content_hash is '文件内容SHA-256哈希，用于去重';
comment on column v5ai_knowledge_document.resource_id is '关联资源库 plm_resource.id';

-- ============================================================
-- V24-4：v5ai_knowledge_chunk 扩展
-- 说明：
--   1) 新增 6 列：paragraph_index（段落索引）/ token_count（分片
--      token 数量）/ vector_id（向量 id）/ content_hash（chunk 内容
--      SHA-256，用于向量去重）/ source_type（chunk 来源：TEXT=文本
--      IMAGE=图片）/ updated_at（更新时间）；
--   2) 新增组合索引（knowledge_base_id 上的 rag / hash / source_type），
--      document_id 已由既有索引 idx_v5ai_knowledge_chunk_document 覆盖，
--      不再重复创建；
--   3) 幂等：add column / create index 均以防重写法实现。
-- ============================================================

alter table v5ai_knowledge_chunk
    add column if not exists paragraph_index int,
    add column if not exists token_count int,
    add column if not exists vector_id varchar(128),
    add column if not exists content_hash varchar(64),
    add column if not exists source_type varchar(20) not null default 'TEXT',
    add column if not exists updated_at timestamptz not null default current_timestamp;

comment on column v5ai_knowledge_chunk.paragraph_index is '段落索引';
comment on column v5ai_knowledge_chunk.token_count is '分片token数量';
comment on column v5ai_knowledge_chunk.vector_id is '向量id';
comment on column v5ai_knowledge_chunk.content_hash is 'chunk内容SHA-256，用于向量去重';
comment on column v5ai_knowledge_chunk.source_type is 'chunk来源类型: TEXT=文本 IMAGE=图片';
comment on column v5ai_knowledge_chunk.updated_at is '更新时间';

create index if not exists idx_knowledge_chunk_rag on v5ai_knowledge_chunk (knowledge_base_id);
create index if not exists idx_chunk_knowledge_hash on v5ai_knowledge_chunk (knowledge_base_id, content_hash);
create index if not exists idx_chunk_knowledge_source_type on v5ai_knowledge_chunk (knowledge_base_id, source_type);


-- ============================================================
-- V24-5：v5ai_model 扩展
-- 说明：
--   1) 新增 7 列：model_name（模型名称，存量行用 model_key 回填后
--      置 NOT NULL）/ description（模型描述）/ adapter_key（底层
--      协议适配器标识，如 openai-compatible/http）/ config（模型
--      参数配置，JSONB，对应 ConfigExtAttrsDTO）/ scope（模型作用
--      域：GLOBAL=全局 PERSONAL=个人）/ is_default（是否为默认模
--      型）/ owner_id（所有者 ID，NULL=全局，具体值=用户 ID）；
--   2) 更新 model_type 列注释（CHAT/EMBEDDING/RERANKER/IMAGE/SPEECH）；
--   3) 幂等：add column / comment 均以防重写法实现。
-- ============================================================

alter table v5ai_model
    add column if not exists model_name varchar(255),
    add column if not exists description varchar(1000),
    add column if not exists adapter_key varchar(100),
    add column if not exists config jsonb,
    add column if not exists scope varchar(20) not null default 'GLOBAL',
    add column if not exists is_default boolean not null default false,
    add column if not exists owner_id bigint;

-- 存量模型没有名称列，回填 model_key 作为默认名称
update v5ai_model
set model_name = model_key
where model_name is null or model_name = '';

alter table v5ai_model
    alter column model_name set not null;

comment on column v5ai_model.model_name is '模型名称';
comment on column v5ai_model.description is '模型描述';
comment on column v5ai_model.adapter_key is '底层协议适配器标识(openai-compatible/http等)';
comment on column v5ai_model.config is '模型参数配置(JSON格式)';
comment on column v5ai_model.scope is '模型作用域: GLOBAL=全局 PERSONAL=个人';
comment on column v5ai_model.is_default is '是否为默认模型';
comment on column v5ai_model.owner_id is '所有者ID(NULL=全局,具体值=用户ID)';
comment on column v5ai_model.model_type is '模型类型(CHAT/EMBEDDING/RERANKER/IMAGE/SPEECH)';
