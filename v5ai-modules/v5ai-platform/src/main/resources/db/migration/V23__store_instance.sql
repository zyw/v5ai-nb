-- ============================================================
-- V23：存储实例（v5ai_store_instance）+ 「资源」菜单
-- 说明：
--   1) 新增存储实例表：向量库 / 搜索引擎实例配置
--      （name/category/type/config/status/is_default）；
--      时间列沿用仓库约定 created_at/updated_at（TIMESTAMPTZ，
--      由 MyBatis-Plus BaseEntity 自动填充，不使用触发器）；
--   2) 在「资源」目录下新增「存储实例」菜单（含按钮权限）并授权给
--      ADMIN；幂等：菜单与授权均以 NOT EXISTS 防重。
-- ============================================================

-- ---------- 1. 存储实例表 ----------

create table if not exists v5ai_store_instance
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    category   SMALLINT     NOT NULL,
    type       SMALLINT     NOT NULL,
    config     TEXT         DEFAULT NULL,
    status     SMALLINT     DEFAULT 1,
    is_default BOOLEAN      DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

comment on table v5ai_store_instance is '存储实例';
comment on column v5ai_store_instance.id is '主键';
comment on column v5ai_store_instance.name is '实例名称';
comment on column v5ai_store_instance.category is '分类: 1-向量库 2-搜索引擎';
comment on column v5ai_store_instance.type is '类型: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-PG_FULLTEXT';
comment on column v5ai_store_instance.config is '连接参数 JSON';
comment on column v5ai_store_instance.status is '状态: 0-停用 1-启用';
comment on column v5ai_store_instance.is_default is '是否为该 category 下默认实例';
comment on column v5ai_store_instance.created_at is '创建时间';
comment on column v5ai_store_instance.updated_at is '更新时间';

create index if not exists idx_store_instance_category on v5ai_store_instance (category);
create index if not exists idx_store_instance_type on v5ai_store_instance (type);

-- ---------- 2. 「存储实例」菜单（资源组，order_num=17） ----------

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例', id, 17, 'store-instances', 'resource/store-instances/index', 'C', '0', '0', 'rag:store:list', 'store-instances'
from plm_menu
where menu_name = '资源'
  and not exists (select 1 from plm_menu where menu_name = '存储实例');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例查询', id, 1, '', '', 'F', '0', '0', 'rag:store:query', '#'
from plm_menu
where menu_name = '存储实例'
  and not exists (select 1 from plm_menu where perms = 'rag:store:query');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例新增', id, 2, '', '', 'F', '0', '0', 'rag:store:add', '#'
from plm_menu
where menu_name = '存储实例'
  and not exists (select 1 from plm_menu where perms = 'rag:store:add');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例修改', id, 3, '', '', 'F', '0', '0', 'rag:store:edit', '#'
from plm_menu
where menu_name = '存储实例'
  and not exists (select 1 from plm_menu where perms = 'rag:store:edit');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例删除', id, 4, '', '', 'F', '0', '0', 'rag:store:remove', '#'
from plm_menu
where menu_name = '存储实例'
  and not exists (select 1 from plm_menu where perms = 'rag:store:remove');

-- ---------- 3. 新菜单授权给 ADMIN（幂等） ----------

insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and m.menu_name in ('存储实例', '存储实例查询', '存储实例新增', '存储实例修改', '存储实例删除')
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);
