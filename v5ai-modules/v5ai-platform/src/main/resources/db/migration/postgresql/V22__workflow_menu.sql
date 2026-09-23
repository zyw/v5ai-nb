-- ============================================================
-- V22：补齐「工作流」菜单
-- 背景：V12 已将「工作流」菜单种入 v5ai_menu（资源组下，sort_order=16，
-- 仅授权 ADMIN）；V18 RBAC 重建（DROP v5ai_menu 等旧表并重建为
-- plm_menu）时重种的菜单树遗漏了该菜单，导致侧边栏「工作流」缺失。
-- 本次在「资源」目录下按 V12 的配置补齐菜单行并重新授权给 ADMIN
-- （授权范围与 V12 一致；图标 workflows 已映射到前端 AppLayout 的
-- lucide Workflow 组件，前端路由 /workflows -> WorkflowsView 已存在）。
-- 幂等：菜单与授权均以 NOT EXISTS 防重，避免与菜单管理界面手动创建冲突。
-- 背景：当前项目不需要租户功能。V7 创建的 v5ai_tenant 表在
-- V18 RBAC 重建（v5ai_user 重建为 plm_user，不再有 tenant_id）后
-- 已成为孤儿表；「租户管理」菜单（plm_menu）及其 ADMIN 授权
-- （plm_role_menu）一并移除。历史迁移保持只增不改。
-- 注意：V18 的 plm_role_menu 无外键约束，需先删授权行再删菜单行。
-- ============================================================

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流', id, 16, 'workflows', 'resource/workflows/index', 'C', '0', '0', NULL, 'workflows'
from plm_menu
where menu_name = '资源'
  and not exists (select 1 from plm_menu where menu_name = '工作流');

insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and m.menu_name = '工作流'
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);

-- 1. 删除「租户管理」菜单的授权行（先删子表）
delete from plm_role_menu
where menu_id in (select id from plm_menu where menu_name = '租户管理');

-- 2. 删除「租户管理」菜单行
delete from plm_menu where menu_name = '租户管理';

-- 3. 删除孤儿表 v5ai_tenant
drop table if exists v5ai_tenant;
