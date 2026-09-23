-- ============================================================
-- V21：菜单权限标识补齐 + 新增「日志管理」「客户端管理」菜单
-- 说明：
--   1) 为既有管理菜单补 perms（按钮 F 挂 add/edit/remove 等权限码），
--      否则新 Plm* 控制器的 @SaCheckPermission 全部 403；
--   2) 新增「客户端管理」「日志管理」菜单（含按钮权限）并授权给 ADMIN；
--   3) 把种子 admin 账号设为超管（id = SystemConstants.SUPER_ADMIN_USER_ID），
--      使其权限集合为 *:*:*（plm_user_role 引用一并更新，无外键约束）。
-- ============================================================

-- ---------- 1. 既有菜单补 perms ----------

update plm_menu set perms = 'system:user:list' where menu_name = '用户管理' and perms is null;
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('用户查询', (select id from plm_menu where menu_name = '用户管理'), 1,  '', '', 'F', '0', '0', 'system:user:query',  '#'),
    ('用户新增', (select id from plm_menu where menu_name = '用户管理'), 2,  '', '', 'F', '0', '0', 'system:user:add',    '#'),
    ('用户修改', (select id from plm_menu where menu_name = '用户管理'), 3,  '', '', 'F', '0', '0', 'system:user:edit',   '#'),
    ('用户删除', (select id from plm_menu where menu_name = '用户管理'), 4,  '', '', 'F', '0', '0', 'system:user:remove', '#'),
    ('重置密码', (select id from plm_menu where menu_name = '用户管理'), 5,  '', '', 'F', '0', '0', 'system:user:resetPwd', '#');

update plm_menu set perms = 'system:role:list' where menu_name = '角色管理' and perms is null;
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('角色查询', (select id from plm_menu where menu_name = '角色管理'), 1, '', '', 'F', '0', '0', 'system:role:query',  '#'),
    ('角色新增', (select id from plm_menu where menu_name = '角色管理'), 2, '', '', 'F', '0', '0', 'system:role:add',    '#'),
    ('角色修改', (select id from plm_menu where menu_name = '角色管理'), 3, '', '', 'F', '0', '0', 'system:role:edit',   '#'),
    ('角色删除', (select id from plm_menu where menu_name = '角色管理'), 4, '', '', 'F', '0', '0', 'system:role:remove', '#');

update plm_menu set perms = 'system:menu:list' where menu_name = '菜单管理' and perms is null;
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('菜单查询', (select id from plm_menu where menu_name = '菜单管理'), 1, '', '', 'F', '0', '0', 'system:menu:query',  '#'),
    ('菜单新增', (select id from plm_menu where menu_name = '菜单管理'), 2, '', '', 'F', '0', '0', 'system:menu:add',    '#'),
    ('菜单修改', (select id from plm_menu where menu_name = '菜单管理'), 3, '', '', 'F', '0', '0', 'system:menu:edit',   '#'),
    ('菜单删除', (select id from plm_menu where menu_name = '菜单管理'), 4, '', '', 'F', '0', '0', 'system:menu:remove', '#');

-- ---------- 2. 新增「客户端管理」（系统管理下） ----------

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('客户端管理', (select id from plm_menu where menu_name = '系统管理'), 25, 'system/clients', 'system/clients/index', 'C', '0', '0', 'system:client:list', 'clients');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('客户端查询', (select id from plm_menu where menu_name = '客户端管理' and perms = 'system:client:list'), 1, '', '', 'F', '0', '0', 'system:client:query',  '#'),
    ('客户端新增', (select id from plm_menu where menu_name = '客户端管理' and perms = 'system:client:list'), 2, '', '', 'F', '0', '0', 'system:client:add',    '#'),
    ('客户端修改', (select id from plm_menu where menu_name = '客户端管理' and perms = 'system:client:list'), 3, '', '', 'F', '0', '0', 'system:client:edit',   '#'),
    ('客户端删除', (select id from plm_menu where menu_name = '客户端管理' and perms = 'system:client:list'), 4, '', '', 'F', '0', '0', 'system:client:remove', '#');

-- ---------- 3. 新增「日志管理」（系统管理下，单页双 tab：登录日志/操作日志） ----------

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('日志管理', (select id from plm_menu where menu_name = '系统管理'), 26, 'system/logs', 'system/logs/index', 'C', '0', '0', 'monitor:logininfo:list', 'logs');

insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('登录日志删除', (select id from plm_menu where menu_name = '日志管理' and perms = 'monitor:logininfo:list'), 1, '', '', 'F', '0', '0', 'monitor:logininfo:remove', '#'),
    ('账户解锁',   (select id from plm_menu where menu_name = '日志管理' and perms = 'monitor:logininfo:list'), 2, '', '', 'F', '0', '0', 'monitor:logininfo:unlock', '#'),
    ('操作日志查询', (select id from plm_menu where menu_name = '日志管理' and perms = 'monitor:logininfo:list'), 3, '', '', 'F', '0', '0', 'monitor:operlog:list',    '#'),
    ('操作日志删除', (select id from plm_menu where menu_name = '日志管理' and perms = 'monitor:logininfo:list'), 4, '', '', 'F', '0', '0', 'monitor:operlog:remove',  '#');

-- ---------- 4. 新菜单授权给 ADMIN（幂等：不存在才插入） ----------

insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);

-- ---------- 5. 种子 admin 账号超管化 ----------

update plm_user_role
set user_id = 1
where user_id in (select id from plm_user where user_name = 'admin')
  and user_id <> 1761100000000000001;

update plm_user
set id = 1
where user_name = 'admin'
  and id <> 1761100000000000001;
