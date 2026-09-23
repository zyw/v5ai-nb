-- ============================================================
-- V25-1：v5ai_store_instance 新增 description 列
-- 说明：
--   1) 新增实例描述列 description（VARCHAR(1000)，与 v5ai_model.description
--      长度约定一致），用于展示实例用途/备注；存量行默认为 NULL；
--   2) 幂等：add column 以防重写法实现。
-- ============================================================

alter table v5ai_store_instance
    add column if not exists description varchar(1000);

comment on column v5ai_store_instance.description is '实例描述';

-- ============================================================
-- V25-2：将「资源存储」菜单从「资源」迁移到「系统管理」下
-- 说明：
--   1) 仅更新 parent_id / order_num（菜单 ID 不变，ADMIN 授权
--      plm_role_menu 按 menu_id 关联，自动跟随，无需重授权；
--      按钮权限（查询/上传/修改/删除）为「资源存储」子节点，
--      随父级一起移动）；
--   2) order_num 取 27，排在系统管理现有子菜单（21-26）之后；
--      路由 path 保持 'resources' 不变（前端已有同名路由）；
--   3) 幂等：仅当「资源存储」仍挂在「资源」下时才执行迁移，
--      重复执行不生效。
-- ============================================================

update plm_menu
set parent_id = (select id from plm_menu where menu_name = '系统管理'),
    order_num  = 27
where menu_name = '资源存储'
  and parent_id = (select id from plm_menu where menu_name = '资源');
