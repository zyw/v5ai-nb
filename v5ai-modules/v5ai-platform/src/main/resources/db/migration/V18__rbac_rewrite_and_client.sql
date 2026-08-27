-- V18: RBAC 表整体替换为 RuoYi 风格 + 新增系统授权表 plm_client
-- 背景：V7/V9/V10 落地的是 role_key 字符串关联结构（v5ai_role 以 role_key 为主键、
-- v5ai_user_role / v5ai_role_menu 存 role_key）。本次按新约定整体替换为：
--   角色表 id 主键 + role_key 业务键；关联表存 role_id；
--   用户表 user_name / password / nick_name 等 RuoYi 风格列。
-- 结构差异过大无法 ALTER 平滑迁移，采用 DROP 重建；末尾回填基础数据保持系统可用。
-- 注意：旧表数据（原 admin 账号、角色、菜单、绑定）会清空。

-- 1. 删除旧表（依赖顺序：先删关联表，再删被引用表）
DROP TABLE IF EXISTS v5ai_audit_log;
DROP TABLE IF EXISTS v5ai_role_menu;
DROP TABLE IF EXISTS v5ai_user_role;
DROP TABLE IF EXISTS v5ai_menu;
DROP TABLE IF EXISTS v5ai_role;
DROP TABLE IF EXISTS v5ai_user;

-- ----------------------------
-- 2、用户信息表
-- ----------------------------
create table if not exists plm_user
(
    id          BIGSERIAL not null,
    user_name   varchar(30)  not null,
    nick_name   varchar(30)  not null,
    user_type   varchar(10)  default 'sys_user'::varchar,
    email       varchar(50)  default ''::varchar,
    phone_number varchar(11) default ''::varchar,
    gender      char         default '0'::bpchar,
    avatar      int8,
    password    varchar(100) default ''::varchar,
    status      char         default '0'::bpchar,
    del_flag    char         default '0'::bpchar,
    login_ip    varchar(128) default ''::varchar,
    login_date  timestamp,
    created_by   int8,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   int8,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark      varchar(500) default null::varchar,
    constraint "plm_user_pk" primary key (id)
);

create index idx_plm_user_created_by ON plm_user (created_by);
create index idx_plm_user_user_name ON plm_user (user_name);
create index idx_plm_user_phone ON plm_user (phone_number);

comment on table plm_user               is '用户信息表';
comment on column plm_user.id           is '用户ID';
comment on column plm_user.user_name    is '用户账号';
comment on column plm_user.nick_name    is '用户昵称';
comment on column plm_user.user_type    is '用户类型（sys_user系统用户）';
comment on column plm_user.email        is '用户邮箱';
comment on column plm_user.phone_number is '手机号码';
comment on column plm_user.gender       is '用户性别（0男 1女 2未知）';
comment on column plm_user.avatar       is '头像地址';
comment on column plm_user.password     is '密码';
comment on column plm_user.status       is '账号状态（0正常 1停用）';
comment on column plm_user.del_flag     is '删除标志（0代表存在 1代表删除）';
comment on column plm_user.login_ip     is '最后登陆IP';
comment on column plm_user.login_date   is '最后登陆时间';
comment on column plm_user.created_by    is '创建者';
comment on column plm_user.created_at  is '创建时间';
comment on column plm_user.updated_by    is '更新者';
comment on column plm_user.updated_at  is '更新时间';
comment on column plm_user.remark       is '备注';

-- ----------------------------
-- 5、菜单权限表
-- ----------------------------
create table if not exists plm_menu
(
    id          BIGSERIAL not null,
    menu_name   varchar(50) not null,
    parent_id   int8         default 0,
    order_num   int4         default 0,
    path        varchar(200) default ''::varchar,
    component   varchar(255) default null::varchar,
    query_param varchar(255) default null::varchar,
    is_frame    char         default 'N'::bpchar,
    is_cache    char         default 'Y'::bpchar,
    menu_type   char         default ''::bpchar,
    visible     char         default '0'::bpchar,
    status      char         default '0'::bpchar,
    perms       varchar(100) default null::varchar,
    icon        varchar(100) default '#'::varchar,
    active_menu varchar(255) default ''::varchar,
    ext         varchar(2000) default ''::varchar,
    created_by  int8,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  int8,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark      varchar(500) default ''::varchar,
    constraint "plm_menu_pk" primary key (id)
);

comment on table plm_menu               is '菜单权限表';
comment on column plm_menu.id           is '菜单ID';
comment on column plm_menu.menu_name    is '菜单名称';
comment on column plm_menu.parent_id    is '父菜单ID';
comment on column plm_menu.order_num    is '显示顺序';
comment on column plm_menu.path         is '路由地址';
comment on column plm_menu.component    is '组件路径';
comment on column plm_menu.query_param  is '路由参数';
comment on column plm_menu.is_frame     is '是否为外链（Y是 N否）';
comment on column plm_menu.is_cache     is '是否缓存（Y缓存 N不缓存）';
comment on column plm_menu.menu_type    is '菜单类型（M目录 C菜单 F按钮）';
comment on column plm_menu.visible      is '显示状态（0显示 1隐藏）';
comment on column plm_menu.status       is '菜单状态（0正常 1停用）';
comment on column plm_menu.perms        is '权限标识';
comment on column plm_menu.icon         is '菜单图标';
comment on column plm_menu.created_by   is '创建者';
comment on column plm_menu.created_at   is '创建时间';
comment on column plm_menu.updated_by   is '更新者';
comment on column plm_menu.updated_at   is '更新时间';
comment on column plm_menu.active_menu  is '激活菜单路径';
comment on column plm_menu.ext          is '扩展字段';
comment on column plm_menu.remark       is '备注';

-- ----------------------------
-- 4、角色信息表
-- ----------------------------
create table if not exists plm_role
(
    id                  BIGSERIAL not null,
    role_name           varchar(30)  not null,
    role_key            varchar(100) not null,
    role_sort           int4         not null,
    data_scope          char         default '1'::bpchar,
    menu_check_strictly bool         default true,
    dept_check_strictly bool         default true,
    status              char         not null,
    del_flag            char         default '0'::bpchar,
    created_by          int8,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          int8,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark              varchar(500) default null::varchar,
    constraint "plm_role_pk" primary key (id)
);

create index idx_plm_role_created_by ON plm_role (created_by);

comment on table plm_role                       is '角色信息表';
comment on column plm_role.id                   is '角色ID';
comment on column plm_role.role_name            is '角色名称';
comment on column plm_role.role_key             is '角色权限字符串';
comment on column plm_role.role_sort            is '显示顺序';
comment on column plm_role.data_scope           is '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限 5：仅本人数据权限 6：部门及以下或本人数据权限）';
comment on column plm_role.menu_check_strictly  is '菜单树选择项是否关联显示';
comment on column plm_role.dept_check_strictly  is '部门树选择项是否关联显示';
comment on column plm_role.status               is '角色状态（0正常 1停用）';
comment on column plm_role.del_flag             is '删除标志（0代表存在 1代表删除）';
comment on column plm_role.created_by            is '创建者';
comment on column plm_role.created_at          is '创建时间';
comment on column plm_role.updated_by            is '更新者';
comment on column plm_role.updated_at          is '更新时间';
comment on column plm_role.remark               is '备注';

-- ----------------------------
-- 6、用户和角色关联表  用户N-1角色
-- ----------------------------
create table if not exists plm_user_role
(
    user_id int8 not null,
    role_id int8 not null,
    constraint plm_user_role_pk primary key (user_id, role_id)
);

create index idx_plm_user_role_rid ON plm_user_role (role_id);

comment on table plm_user_role              is '用户和角色关联表';
comment on column plm_user_role.user_id     is '用户ID';
comment on column plm_user_role.role_id     is '角色ID';

-- ----------------------------
-- 7、角色和菜单关联表  角色1-N菜单
-- ----------------------------
create table if not exists plm_role_menu
(
    role_id int8 not null,
    menu_id int8 not null,
    constraint plm_role_menu_pk primary key (role_id, menu_id)
);

comment on table plm_role_menu              is '角色和菜单关联表';
comment on column plm_role_menu.role_id     is '角色ID';
comment on column plm_role_menu.menu_id     is '菜单ID';

-- ----------------------------
-- 系统授权表
-- ----------------------------
create table plm_client (
    id                  BIGSERIAL not null,
    client_id           varchar(64)   default ''::varchar,
    client_key          varchar(32)   default ''::varchar,
    client_secret       varchar(255)  default ''::varchar,
    grant_type          varchar(255)  default ''::varchar,
    device_type         varchar(32)   default ''::varchar,
    access_path         varchar(2000) default ''::varchar,
    ip_whitelist        varchar(1000) default ''::varchar,
    active_timeout      int4          default 1800,
    timeout             int4          default 604800,
    status              char(1)       default '0'::bpchar,
    del_flag            char(1)       default '0'::bpchar,
    created_by           int8,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           int8,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    constraint plm_client_pk primary key (id)
);

comment on table plm_client                         is '系统授权表';
comment on column plm_client.id                     is '主键';
comment on column plm_client.client_id              is '客户端id';
comment on column plm_client.client_key             is '客户端key';
comment on column plm_client.client_secret          is '客户端秘钥';
comment on column plm_client.grant_type             is '授权类型';
comment on column plm_client.device_type            is '设备类型';
comment on column plm_client.access_path            is '允许访问路径';
comment on column plm_client.ip_whitelist           is 'IP白名单';
comment on column plm_client.active_timeout         is 'token活跃超时时间';
comment on column plm_client.timeout                is 'token固定超时';
comment on column plm_client.status                 is '状态（0正常 1停用）';
comment on column plm_client.del_flag               is '删除标志（0代表存在 1代表删除）';
comment on column plm_client.created_by              is '创建者';
comment on column plm_client.created_at            is '创建时间';
comment on column plm_client.updated_by              is '更新者';
comment on column plm_client.updated_at            is '更新时间';

insert into plm_client values (1, 'e5cd7e4891bf95d1d19206ce24a7b32e', 'pc', 'pc123', 'password', 'pc', '', '', 1800, 604800, 0, 0, 1761100000000000001, now(), 1761100000000000001, now());

-- ----------------------------
-- 8、回填基础数据（结构重建后系统保持可用）
-- ----------------------------

-- 角色：role_key 与 RoleKey 枚举（ADMIN/USER）保持一致
INSERT INTO plm_role (role_name, role_key, role_sort, data_scope, status, del_flag) VALUES
    ('超级管理员', 'ADMIN', 1, '1', '0', '0'),
    ('普通用户',   'USER',  2, '5', '0', '0');

-- 管理员账号：密码为 BCrypt 哈希，明文 admin（见 docs/deploy/dev.md 4.3）
INSERT INTO plm_user (user_name, nick_name, user_type, password, status, del_flag) VALUES
    ('admin', '管理员', 'sys_user', '$2a$10$muicVGDQ5Nvw70.tEnPOnuLjM8lftXnTZ0e.61azAiV6QRuiK7WT6', '0', '0');

-- 菜单树：沿用原有导航结构（目录 M / 菜单 C，与 menu_type 注释约定一致），
-- path/component 为占位值，待前端适配时调整
INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, icon) VALUES
    ('总览', 0, 1,  'dashboard', 'dashboard/index',       'C', '0', '0', 'dashboard'),
    ('资源',   0, 10, 'resource', NULL,                   'M', '0', '0', 'resource'),
    ('系统管理', 0, 20, 'system',   NULL,                   'M', '0', '0', 'system'),
    ('运维',   0, 30, 'ops',      NULL,                   'M', '0', '0', 'ops');

INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, icon) VALUES
    ('模型管理',   (SELECT id FROM plm_menu WHERE menu_name = '资源'),    11, 'models',             'resource/models/index',    'C', '0', '0', 'models'),
    ('Agent 管理', (SELECT id FROM plm_menu WHERE menu_name = '资源'),    12, 'agents',             'resource/agents/index',    'C', '0', '0', 'agents'),
    ('知识库管理', (SELECT id FROM plm_menu WHERE menu_name = '资源'),    13, 'knowledge-bases',    'resource/knowledge-bases/index', 'C', '0', '0', 'knowledge-bases'),
    ('MCP 管理',   (SELECT id FROM plm_menu WHERE menu_name = '资源'),    14, 'mcp-servers',        'resource/mcp-servers/index','C', '0', '0', 'mcp-servers'),
    ('Skill 管理', (SELECT id FROM plm_menu WHERE menu_name = '资源'),    15, 'skills',             'resource/skills/index',    'C', '0', '0', 'skills'),
    ('用户管理',   (SELECT id FROM plm_menu WHERE menu_name = '系统管理'), 21, 'system/users',       'system/users/index',       'C', '0', '0', 'users'),
    ('角色管理',   (SELECT id FROM plm_menu WHERE menu_name = '系统管理'), 22, 'system/roles',       'system/roles/index',       'C', '0', '0', 'roles'),
    ('菜单管理',   (SELECT id FROM plm_menu WHERE menu_name = '系统管理'), 23, 'system/menus',       'system/menus/index',       'C', '0', '0', 'menus'),
    ('租户管理',   (SELECT id FROM plm_menu WHERE menu_name = '系统管理'), 24, 'system/tenants',     'system/tenants/index',     'C', '0', '0', 'tenants'),
    ('可观测性',   (SELECT id FROM plm_menu WHERE menu_name = '运维'),    31, 'observability',      'ops/observability/index',  'C', '0', '0', 'observability'),
    ('调试工具',   (SELECT id FROM plm_menu WHERE menu_name = '运维'),    32, 'chat',               'ops/chat/index',           'C', '0', '0', 'chat'),
    ('系统信息',   (SELECT id FROM plm_menu WHERE menu_name = '运维'),    33, 'settings',           'ops/settings/index',       'C', '0', '0', 'settings');

-- 角色-菜单关联：ADMIN 全部菜单；USER 总览 + 资源组（沿用 V10 的分配规则）
INSERT INTO plm_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM plm_role r, plm_menu m WHERE r.role_key = 'ADMIN';

INSERT INTO plm_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM plm_role r, plm_menu m
WHERE r.role_key = 'USER'
  AND (m.menu_name = '总览'
       OR m.parent_id = (SELECT id FROM plm_menu WHERE menu_name = '资源'));

-- 用户-角色关联：admin -> ADMIN
INSERT INTO plm_user_role (user_id, role_id)
SELECT u.id, r.id FROM plm_user u, plm_role r
WHERE u.user_name = 'admin' AND r.role_key = 'ADMIN';
