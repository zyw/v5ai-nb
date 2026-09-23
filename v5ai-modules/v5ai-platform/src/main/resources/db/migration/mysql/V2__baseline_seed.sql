-- =============================================================================
-- MySQL 基线种子数据：等价于 PostgreSQL 侧 V18/V21/V22/V23/V24/V27/V35/V49 的
-- 全部种子 DML 按同一顺序重放后的最终态（admin 账号、两个角色、客户端、
-- 完整菜单树含按钮权限、角色-菜单授权、12 个模型供应商）。
--
-- 为什么重放而不是直接 dump 最终态：重放能逐条对账到 PG 迁移原文，
-- 「菜单少了几个」这种错在两份文件里都能一眼看到；手推最终态不行。
-- 逐条来源标注在每个语句上方的 -- source: 行。
--
-- 只做了三类语法等价改写，语义一律不动：
--   1) INSERT ... VALUES 里的 (select id from plm_menu where ...) 标量子查询
--      提到语句前成为 SET @pid_N = (SELECT ...)——VALUES 中引用同表的子查询在
--      MySQL 不是标准写法，改成变量后语义唯一、可读；DELETE/UPDATE 里的子查询
--      是 MySQL 合法写法，原样保留。
--   2) V27 的 ON CONFLICT (provider_key) DO NOTHING → ON DUPLICATE KEY UPDATE
--      provider_key = provider_key（同键不覆盖、不重复插入）。
--   3) V49 前四条 `select ... where not exists (...)` 无 FROM → 补 from DUAL；
--      V18 的 plm_client 隐式列插入 → 写出显式列名。
--
-- 全新库上 V21/V22/V24/V35 里的若干 UPDATE/DELETE 命中 0 行（它们是为存量数据
-- 回填/清理写的），一并保留以对齐 PG 侧的执行序列。
-- 幂等：菜单行用 NOT EXISTS 防重（与 PG 侧一致）；供应商用 ON DUPLICATE 防重；
-- V18 的初始菜单/角色/用户/客户端为裸 INSERT，Flyway 记账保证只执行一次。
-- =============================================================================

-- source: V18__rbac_rewrite_and_client.sql
INSERT INTO plm_client (id, client_id, client_key, client_secret, grant_type, device_type, access_path, ip_whitelist, active_timeout, timeout, status, del_flag, created_by, created_at, updated_by, updated_at) VALUES (1, 'e5cd7e4891bf95d1d19206ce24a7b32e', 'pc', 'pc123', 'password', 'pc', '', '', 1800, 604800, 0, 0, 1761100000000000001, now(), 1761100000000000001, now());

-- source: V18__rbac_rewrite_and_client.sql
INSERT INTO plm_role (role_name, role_key, role_sort, data_scope, status, del_flag) VALUES
    ('超级管理员', 'ADMIN', 1, '1', '0', '0'),
    ('普通用户',   'USER',  2, '5', '0', '0');

-- source: V18__rbac_rewrite_and_client.sql
INSERT INTO plm_user (user_name, nick_name, user_type, password, status, del_flag) VALUES
    ('admin', '管理员', 'sys_user', '$2a$10$muicVGDQ5Nvw70.tEnPOnuLjM8lftXnTZ0e.61azAiV6QRuiK7WT6', '0', '0');

-- source: V18__rbac_rewrite_and_client.sql
INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, icon) VALUES
    ('总览', 0, 1,  'dashboard', 'dashboard/index',       'C', '0', '0', 'dashboard'),
    ('资源',   0, 10, 'resource', NULL,                   'M', '0', '0', 'resource'),
    ('系统管理', 0, 20, 'system',   NULL,                   'M', '0', '0', 'system'),
    ('运维',   0, 30, 'ops',      NULL,                   'M', '0', '0', 'ops');

-- source: V18__rbac_rewrite_and_client.sql
SET @pid_1 = (SELECT id FROM plm_menu WHERE menu_name = '资源');
SET @pid_2 = (SELECT id FROM plm_menu WHERE menu_name = '系统管理');
SET @pid_3 = (SELECT id FROM plm_menu WHERE menu_name = '运维');
INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, icon) VALUES
    ('模型管理',   @pid_1,    11, 'models',             'resource/models/index',    'C', '0', '0', 'models'),
    ('Agent 管理', @pid_1,    12, 'agents',             'resource/agents/index',    'C', '0', '0', 'agents'),
    ('知识库管理', @pid_1,    13, 'knowledge-bases',    'resource/knowledge-bases/index', 'C', '0', '0', 'knowledge-bases'),
    ('MCP 管理',   @pid_1,    14, 'mcp-servers',        'resource/mcp-servers/index','C', '0', '0', 'mcp-servers'),
    ('Skill 管理', @pid_1,    15, 'skills',             'resource/skills/index',    'C', '0', '0', 'skills'),
    ('用户管理',   @pid_2, 21, 'system/users',       'system/users/index',       'C', '0', '0', 'users'),
    ('角色管理',   @pid_2, 22, 'system/roles',       'system/roles/index',       'C', '0', '0', 'roles'),
    ('菜单管理',   @pid_2, 23, 'system/menus',       'system/menus/index',       'C', '0', '0', 'menus'),
    ('租户管理',   @pid_2, 24, 'system/tenants',     'system/tenants/index',     'C', '0', '0', 'tenants'),
    ('可观测性',   @pid_3,    31, 'observability',      'ops/observability/index',  'C', '0', '0', 'observability'),
    ('调试工具',   @pid_3,    32, 'chat',               'ops/chat/index',           'C', '0', '0', 'chat'),
    ('系统信息',   @pid_3,    33, 'settings',           'ops/settings/index',       'C', '0', '0', 'settings');

-- source: V18__rbac_rewrite_and_client.sql
INSERT INTO plm_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM plm_role r, plm_menu m WHERE r.role_key = 'ADMIN';

-- source: V18__rbac_rewrite_and_client.sql
INSERT INTO plm_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM plm_role r, plm_menu m
WHERE r.role_key = 'USER'
  AND (m.menu_name = '总览'
       OR m.parent_id = (SELECT id FROM plm_menu WHERE menu_name = '资源'));

-- source: V18__rbac_rewrite_and_client.sql
INSERT INTO plm_user_role (user_id, role_id)
SELECT u.id, r.id FROM plm_user u, plm_role r
WHERE u.user_name = 'admin' AND r.role_key = 'ADMIN';

-- source: V21__menu_perms_log_and_client.sql
update plm_menu set perms = 'system:user:list' where menu_name = '用户管理' and perms is null;

-- source: V21__menu_perms_log_and_client.sql
SET @pid_4 = (SELECT id FROM plm_menu WHERE menu_name = '用户管理');
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('用户查询', @pid_4, 1,  '', '', 'F', '0', '0', 'system:user:query',  '#'),
    ('用户新增', @pid_4, 2,  '', '', 'F', '0', '0', 'system:user:add',    '#'),
    ('用户修改', @pid_4, 3,  '', '', 'F', '0', '0', 'system:user:edit',   '#'),
    ('用户删除', @pid_4, 4,  '', '', 'F', '0', '0', 'system:user:remove', '#'),
    ('重置密码', @pid_4, 5,  '', '', 'F', '0', '0', 'system:user:resetPwd', '#');

-- source: V21__menu_perms_log_and_client.sql
update plm_menu set perms = 'system:role:list' where menu_name = '角色管理' and perms is null;

-- source: V21__menu_perms_log_and_client.sql
SET @pid_5 = (SELECT id FROM plm_menu WHERE menu_name = '角色管理');
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('角色查询', @pid_5, 1, '', '', 'F', '0', '0', 'system:role:query',  '#'),
    ('角色新增', @pid_5, 2, '', '', 'F', '0', '0', 'system:role:add',    '#'),
    ('角色修改', @pid_5, 3, '', '', 'F', '0', '0', 'system:role:edit',   '#'),
    ('角色删除', @pid_5, 4, '', '', 'F', '0', '0', 'system:role:remove', '#');

-- source: V21__menu_perms_log_and_client.sql
update plm_menu set perms = 'system:menu:list' where menu_name = '菜单管理' and perms is null;

-- source: V21__menu_perms_log_and_client.sql
SET @pid_6 = (SELECT id FROM plm_menu WHERE menu_name = '菜单管理');
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('菜单查询', @pid_6, 1, '', '', 'F', '0', '0', 'system:menu:query',  '#'),
    ('菜单新增', @pid_6, 2, '', '', 'F', '0', '0', 'system:menu:add',    '#'),
    ('菜单修改', @pid_6, 3, '', '', 'F', '0', '0', 'system:menu:edit',   '#'),
    ('菜单删除', @pid_6, 4, '', '', 'F', '0', '0', 'system:menu:remove', '#');

-- source: V21__menu_perms_log_and_client.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('客户端管理', @pid_2, 25, 'system/clients', 'system/clients/index', 'C', '0', '0', 'system:client:list', 'clients');

-- source: V21__menu_perms_log_and_client.sql
SET @pid_7 = (SELECT id FROM plm_menu WHERE menu_name = '客户端管理' and perms = 'system:client:list');
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('客户端查询', @pid_7, 1, '', '', 'F', '0', '0', 'system:client:query',  '#'),
    ('客户端新增', @pid_7, 2, '', '', 'F', '0', '0', 'system:client:add',    '#'),
    ('客户端修改', @pid_7, 3, '', '', 'F', '0', '0', 'system:client:edit',   '#'),
    ('客户端删除', @pid_7, 4, '', '', 'F', '0', '0', 'system:client:remove', '#');

-- source: V21__menu_perms_log_and_client.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('日志管理', @pid_2, 26, 'system/logs', 'system/logs/index', 'C', '0', '0', 'monitor:logininfo:list', 'logs');

-- source: V21__menu_perms_log_and_client.sql
SET @pid_8 = (SELECT id FROM plm_menu WHERE menu_name = '日志管理' and perms = 'monitor:logininfo:list');
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) values
    ('登录日志删除', @pid_8, 1, '', '', 'F', '0', '0', 'monitor:logininfo:remove', '#'),
    ('账户解锁',   @pid_8, 2, '', '', 'F', '0', '0', 'monitor:logininfo:unlock', '#'),
    ('操作日志查询', @pid_8, 3, '', '', 'F', '0', '0', 'monitor:operlog:list',    '#'),
    ('操作日志删除', @pid_8, 4, '', '', 'F', '0', '0', 'monitor:operlog:remove',  '#');

-- source: V21__menu_perms_log_and_client.sql
insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);

-- source: V21__menu_perms_log_and_client.sql
update plm_user_role
set user_id = 1
where user_id in (select id from plm_user where user_name = 'admin')
  and user_id <> 1761100000000000001;

-- source: V21__menu_perms_log_and_client.sql
update plm_user
set id = 1
where user_name = 'admin'
  and id <> 1761100000000000001;

-- source: V22__workflow_menu.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流', id, 16, 'workflows', 'resource/workflows/index', 'C', '0', '0', NULL, 'workflows'
from plm_menu
where menu_name = '资源'
  and not exists (select 1 from plm_menu where menu_name = '工作流');

-- source: V22__workflow_menu.sql
insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and m.menu_name = '工作流'
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);

-- source: V22__workflow_menu.sql
delete from plm_role_menu
where menu_id in (select id from plm_menu where menu_name = '租户管理');

-- source: V22__workflow_menu.sql
delete from plm_menu where menu_name = '租户管理';

-- source: V23__store_instance.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例', id, 17, 'store-instances', 'resource/store-instances/index', 'C', '0', '0', 'rag:store:list', 'store-instances'
from plm_menu
where menu_name = '资源'
  and not exists (select 1 from plm_menu where menu_name = '存储实例');

-- source: V23__store_instance.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例查询', id, 1, '', '', 'F', '0', '0', 'rag:store:query', '#'
from plm_menu
where menu_name = '存储实例'
  and not exists (select 1 from plm_menu where perms = 'rag:store:query');

-- source: V23__store_instance.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例新增', id, 2, '', '', 'F', '0', '0', 'rag:store:add', '#'
from plm_menu
where menu_name = '存储实例'
  and not exists (select 1 from plm_menu where perms = 'rag:store:add');

-- source: V23__store_instance.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例修改', id, 3, '', '', 'F', '0', '0', 'rag:store:edit', '#'
from plm_menu
where menu_name = '存储实例'
  and not exists (select 1 from plm_menu where perms = 'rag:store:edit');

-- source: V23__store_instance.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '存储实例删除', id, 4, '', '', 'F', '0', '0', 'rag:store:remove', '#'
from plm_menu
where menu_name = '存储实例'
  and not exists (select 1 from plm_menu where perms = 'rag:store:remove');

-- source: V23__store_instance.sql
insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and m.menu_name in ('存储实例', '存储实例查询', '存储实例新增', '存储实例修改', '存储实例删除')
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);

-- source: V24__knowledge_base_extend.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储', id, 18, 'resources', 'resource/resources/index', 'C', '0', '0', 'platform:resource:list', 'files'
from plm_menu
where menu_name = '资源'
  and not exists (select 1 from plm_menu where menu_name = '资源存储');

-- source: V24__knowledge_base_extend.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储查询', id, 1, '', '', 'F', '0', '0', 'platform:resource:query', '#'
from plm_menu
where menu_name = '资源存储'
  and not exists (select 1 from plm_menu where perms = 'platform:resource:query');

-- source: V24__knowledge_base_extend.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储上传', id, 2, '', '', 'F', '0', '0', 'platform:resource:add', '#'
from plm_menu
where menu_name = '资源存储'
  and not exists (select 1 from plm_menu where perms = 'platform:resource:add');

-- source: V24__knowledge_base_extend.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储修改', id, 3, '', '', 'F', '0', '0', 'platform:resource:edit', '#'
from plm_menu
where menu_name = '资源存储'
  and not exists (select 1 from plm_menu where perms = 'platform:resource:edit');

-- source: V24__knowledge_base_extend.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源存储删除', id, 4, '', '', 'F', '0', '0', 'platform:resource:remove', '#'
from plm_menu
where menu_name = '资源存储'
  and not exists (select 1 from plm_menu where perms = 'platform:resource:remove');

-- source: V24__knowledge_base_extend.sql
insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and m.menu_name in ('资源存储', '资源存储查询', '资源存储上传', '资源存储修改', '资源存储删除')
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);

-- source: V24__knowledge_base_extend.sql
update v5ai_knowledge_document
set source_type = case
                      when source_type like 'http://%' or source_type like 'https://%' then 'URL'
                      else 'UPLOAD'
    end;

-- source: V24__knowledge_base_extend.sql
update v5ai_model
set model_name = model_key
where model_name is null or model_name = '';

-- source: V27__seed_model_providers.sql
INSERT INTO v5ai_model_provider (provider_key, name, enabled, description, icon_url)
VALUES
    ('openai',      'OpenAI',             TRUE, '提供 GPT / o 系列模型（OpenAI 兼容接口）',   '/icons/providers/openai.svg'),
    ('anthropic',   'Anthropic',          TRUE, '提供 Claude 系列模型',                       '/icons/providers/anthropic.svg'),
    ('gemini',      'Google Gemini',      TRUE, '谷歌多模态 Gemini 系列模型',                 '/icons/providers/gemini.svg'),
    ('dashscope',   '阿里云百炼·通义千问',  TRUE, '阿里云大模型服务平台，提供通义千问系列模型',   '/icons/providers/dashscope.svg'),
    ('deepseek',    'DeepSeek',           TRUE, '深度求索，提供 DeepSeek V3 / R1 系列模型',    '/icons/providers/deepseek.svg'),
    ('moonshot',    '月之暗面 Kimi',       TRUE, '提供 Kimi 长文本系列模型',                   '/icons/providers/moonshot.svg'),
    ('zhipu',       '智谱 AI',            TRUE, '提供 GLM 系列模型',                          '/icons/providers/zhipu.svg'),
    ('minimax',     'MiniMax',            TRUE, '提供 MiniMax 系列模型',                      '/icons/providers/minimax.svg'),
    ('tencent',     '腾讯混元',            TRUE, '腾讯混元 Hunyuan 系列模型',                  '/icons/providers/tencent.svg'),
    ('volcengine',  '火山方舟·豆包',        TRUE, '字节跳动大模型服务平台，提供豆包系列模型',      '/icons/providers/volcengine.svg'),
    ('siliconflow', '硅基流动',            TRUE, '一站式大模型 API 平台，聚合开源模型',         '/icons/providers/siliconflow.svg'),
    ('ollama',      'Ollama（本地）',       TRUE, '本地运行开源模型的工具（OpenAI 兼容接口）',    '/icons/providers/ollama.svg')
on duplicate key update provider_key = provider_key;

-- source: V35__api_key_user_agent_binding.sql
DELETE FROM v5ai_api_keys;

-- source: V35__api_key_user_agent_binding.sql
INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'API Keys', id, 18, 'api-keys', 'resource/api-keys/index', 'C', '0', '0', 'apiKeys:keys:list', 'api-keys'
FROM plm_menu
WHERE menu_name = '资源'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE menu_name = 'API Keys');

-- source: V35__api_key_user_agent_binding.sql
INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'Key 查询', id, 1, '', '', 'F', '0', '0', 'apiKeys:keys:query', '#'
FROM plm_menu
WHERE menu_name = 'API Keys'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE perms = 'apiKeys:keys:query');

-- source: V35__api_key_user_agent_binding.sql
INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'Key 新增', id, 2, '', '', 'F', '0', '0', 'apiKeys:keys:add', '#'
FROM plm_menu
WHERE menu_name = 'API Keys'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE perms = 'apiKeys:keys:add');

-- source: V35__api_key_user_agent_binding.sql
INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'Key 修改', id, 3, '', '', 'F', '0', '0', 'apiKeys:keys:edit', '#'
FROM plm_menu
WHERE menu_name = 'API Keys'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE perms = 'apiKeys:keys:edit');

-- source: V35__api_key_user_agent_binding.sql
INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'Key 删除', id, 4, '', '', 'F', '0', '0', 'apiKeys:keys:remove', '#'
FROM plm_menu
WHERE menu_name = 'API Keys'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE perms = 'apiKeys:keys:remove');

-- source: V35__api_key_user_agent_binding.sql
INSERT INTO plm_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM plm_role r, plm_menu m
WHERE r.role_key IN ('ADMIN', 'USER')
  AND m.menu_name IN ('API Keys', 'Key 查询', 'Key 新增', 'Key 修改', 'Key 删除')
  AND NOT EXISTS (SELECT 1 FROM plm_role_menu prm WHERE prm.role_id = r.id AND prm.menu_id = m.id);

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '总览', 0, 1, 'dashboard', 'dashboard/index', 'C', '0', '0', null, 'dashboard'
from DUAL
where not exists (select 1 from plm_menu where menu_name = '总览' and parent_id = 0);

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '资源', 0, 10, 'resource', null, 'M', '0', '0', null, 'resource'
from DUAL
where not exists (select 1 from plm_menu where menu_name = '资源' and parent_id = 0);

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '系统管理', 0, 20, 'system', null, 'M', '0', '0', null, 'system'
from DUAL
where not exists (select 1 from plm_menu where menu_name = '系统管理' and parent_id = 0);

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '运维', 0, 30, 'ops', null, 'M', '0', '0', null, 'ops'
from DUAL
where not exists (select 1 from plm_menu where menu_name = '运维' and parent_id = 0);

-- source: V49__menu_full_button_perms.sql
update plm_menu set perms = 'model:model:list'     where menu_name = '模型管理'   and menu_type = 'C' and perms is null;

-- source: V49__menu_full_button_perms.sql
update plm_menu set perms = 'agent:agent:list'     where menu_name = 'Agent 管理' and menu_type = 'C' and perms is null;

-- source: V49__menu_full_button_perms.sql
update plm_menu set perms = 'rag:knowledge:list'   where menu_name = '知识库管理' and menu_type = 'C' and perms is null;

-- source: V49__menu_full_button_perms.sql
update plm_menu set perms = 'mcp:server:list'      where menu_name = 'MCP 管理'   and menu_type = 'C' and perms is null;

-- source: V49__menu_full_button_perms.sql
update plm_menu set perms = 'skill:skill:list'     where menu_name = 'Skill 管理' and menu_type = 'C' and perms is null;

-- source: V49__menu_full_button_perms.sql
update plm_menu set perms = 'workflow:workflow:list' where menu_name = '工作流'   and menu_type = 'C' and perms is null;

-- source: V49__menu_full_button_perms.sql
update plm_menu set perms = 'monitor:usage:list'   where menu_name = '可观测性'   and menu_type = 'C' and perms is null;

-- source: V49__menu_full_button_perms.sql
update plm_menu set perms = 'monitor:debug:list'   where menu_name = '调试工具'   and menu_type = 'C' and perms is null;

-- source: V49__menu_full_button_perms.sql
update plm_menu set perms = 'monitor:settings:list' where menu_name = '系统信息'  and menu_type = 'C' and perms is null;

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型查询', id, 1, '', '', 'F', '0', '0', 'model:model:query', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:query');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型新增', id, 2, '', '', 'F', '0', '0', 'model:model:add', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:add');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型修改', id, 3, '', '', 'F', '0', '0', 'model:model:edit', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型删除', id, 4, '', '', 'F', '0', '0', 'model:model:remove', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:remove');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型启停', id, 5, '', '', 'F', '0', '0', 'model:model:changeStatus', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:changeStatus');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型连通性测试', id, 6, '', '', 'F', '0', '0', 'model:model:test', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:test');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '模型设为默认', id, 7, '', '', 'F', '0', '0', 'model:model:setDefault', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:model:setDefault');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '供应商查询', id, 8, '', '', 'F', '0', '0', 'model:provider:query', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:provider:query');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '供应商新增', id, 9, '', '', 'F', '0', '0', 'model:provider:add', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:provider:add');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '供应商修改', id, 10, '', '', 'F', '0', '0', 'model:provider:edit', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:provider:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '供应商删除', id, 11, '', '', 'F', '0', '0', 'model:provider:remove', '#'
from plm_menu where menu_name = '模型管理' and not exists (select 1 from plm_menu where perms = 'model:provider:remove');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 查询', id, 1, '', '', 'F', '0', '0', 'agent:agent:query', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:query');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 新增', id, 2, '', '', 'F', '0', '0', 'agent:agent:add', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:add');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 修改', id, 3, '', '', 'F', '0', '0', 'agent:agent:edit', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 删除', id, 4, '', '', 'F', '0', '0', 'agent:agent:remove', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:remove');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 发布', id, 5, '', '', 'F', '0', '0', 'agent:agent:publish', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:publish');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 禁用', id, 6, '', '', 'F', '0', '0', 'agent:agent:disable', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:disable');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 配置生成', id, 7, '', '', 'F', '0', '0', 'agent:agent:generate', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:generate');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Agent 版本查询', id, 8, '', '', 'F', '0', '0', 'agent:agent:version', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:agent:version');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '应用配额查询', id, 9, '', '', 'F', '0', '0', 'agent:quota:query', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:quota:query');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '应用配额修改', id, 10, '', '', 'F', '0', '0', 'agent:quota:edit', '#'
from plm_menu where menu_name = 'Agent 管理' and not exists (select 1 from plm_menu where perms = 'agent:quota:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库查询', id, 1, '', '', 'F', '0', '0', 'rag:knowledge:query', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:query');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库新增', id, 2, '', '', 'F', '0', '0', 'rag:knowledge:add', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:add');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库修改', id, 3, '', '', 'F', '0', '0', 'rag:knowledge:edit', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库删除', id, 4, '', '', 'F', '0', '0', 'rag:knowledge:remove', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:remove');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '知识库启停', id, 5, '', '', 'F', '0', '0', 'rag:knowledge:changeStatus', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:changeStatus');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档查询', id, 6, '', '', 'F', '0', '0', 'rag:document:list', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:list');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档上传', id, 7, '', '', 'F', '0', '0', 'rag:document:add', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:add');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档删除', id, 8, '', '', 'F', '0', '0', 'rag:document:remove', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:remove');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档重新解析', id, 9, '', '', 'F', '0', '0', 'rag:document:reparse', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:reparse');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '文档下载', id, 10, '', '', 'F', '0', '0', 'rag:document:download', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:document:download');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '分片查询', id, 11, '', '', 'F', '0', '0', 'rag:chunk:list', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:chunk:list');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '分片新增', id, 12, '', '', 'F', '0', '0', 'rag:chunk:add', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:chunk:add');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '分片修改', id, 13, '', '', 'F', '0', '0', 'rag:chunk:edit', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:chunk:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '分片删除', id, 14, '', '', 'F', '0', '0', 'rag:chunk:remove', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:chunk:remove');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '召回测试', id, 15, '', '', 'F', '0', '0', 'rag:knowledge:retrieve', '#'
from plm_menu where menu_name = '知识库管理' and not exists (select 1 from plm_menu where perms = 'rag:knowledge:retrieve');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '服务查询', id, 1, '', '', 'F', '0', '0', 'mcp:server:query', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:query');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '服务新增', id, 2, '', '', 'F', '0', '0', 'mcp:server:add', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:add');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '服务修改', id, 3, '', '', 'F', '0', '0', 'mcp:server:edit', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '服务启停', id, 4, '', '', 'F', '0', '0', 'mcp:server:changeStatus', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:changeStatus');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '连接测试', id, 5, '', '', 'F', '0', '0', 'mcp:server:test', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:test');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工具发现', id, 6, '', '', 'F', '0', '0', 'mcp:server:discover', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:server:discover');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工具查询', id, 7, '', '', 'F', '0', '0', 'mcp:tool:list', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:tool:list');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工具权限修改', id, 8, '', '', 'F', '0', '0', 'mcp:tool:edit', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:tool:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '调用记录查询', id, 9, '', '', 'F', '0', '0', 'mcp:toolCall:list', '#'
from plm_menu where menu_name = 'MCP 管理' and not exists (select 1 from plm_menu where perms = 'mcp:toolCall:list');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 查询', id, 1, '', '', 'F', '0', '0', 'skill:skill:query', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:query');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 新增', id, 2, '', '', 'F', '0', '0', 'skill:skill:add', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:add');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 修改', id, 3, '', '', 'F', '0', '0', 'skill:skill:edit', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 删除', id, 4, '', '', 'F', '0', '0', 'skill:skill:remove', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:remove');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 启停', id, 5, '', '', 'F', '0', '0', 'skill:skill:changeStatus', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:changeStatus');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本查询', id, 6, '', '', 'F', '0', '0', 'skill:version:list', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:list');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本删除', id, 7, '', '', 'F', '0', '0', 'skill:version:remove', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:remove');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本发布', id, 8, '', '', 'F', '0', '0', 'skill:version:publish', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:publish');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本下线', id, 9, '', '', 'F', '0', '0', 'skill:version:offline', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:offline');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '版本回滚', id, 10, '', '', 'F', '0', '0', 'skill:version:rollback', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:version:rollback');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 文件编辑', id, 11, '', '', 'F', '0', '0', 'skill:file:edit', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:file:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select 'Skill 内容生成', id, 12, '', '', 'F', '0', '0', 'skill:skill:ai', '#'
from plm_menu where menu_name = 'Skill 管理' and not exists (select 1 from plm_menu where perms = 'skill:skill:ai');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流查询', id, 1, '', '', 'F', '0', '0', 'workflow:workflow:query', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:query');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流新增', id, 2, '', '', 'F', '0', '0', 'workflow:workflow:add', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:add');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流编排', id, 3, '', '', 'F', '0', '0', 'workflow:workflow:edit', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:edit');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流删除', id, 4, '', '', 'F', '0', '0', 'workflow:workflow:remove', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:remove');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流发布', id, 5, '', '', 'F', '0', '0', 'workflow:workflow:publish', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:publish');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '工作流运行', id, 6, '', '', 'F', '0', '0', 'workflow:workflow:run', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:workflow:run');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '运行记录查询', id, 7, '', '', 'F', '0', '0', 'workflow:run:list', '#'
from plm_menu where menu_name = '工作流' and not exists (select 1 from plm_menu where perms = 'workflow:run:list');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '用量查询', id, 1, '', '', 'F', '0', '0', 'monitor:usage:query', '#'
from plm_menu where menu_name = '可观测性' and not exists (select 1 from plm_menu where perms = 'monitor:usage:query');

-- source: V49__menu_full_button_perms.sql
insert into plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
select '调试对话', id, 1, '', '', 'F', '0', '0', 'monitor:debug:chat', '#'
from plm_menu where menu_name = '调试工具' and not exists (select 1 from plm_menu where perms = 'monitor:debug:chat');

-- source: V49__menu_full_button_perms.sql
update plm_menu set order_num = 19 where menu_name = 'API Keys' and menu_type = 'C' and order_num = 18;

-- source: V49__menu_full_button_perms.sql
insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'ADMIN'
  and (m.menu_name in ('总览', '资源', '系统管理', '运维')
    or m.parent_id in (select id from plm_menu where menu_name in ('模型管理', 'Agent 管理', '知识库管理', 'MCP 管理', 'Skill 管理'))
    or m.parent_id in (select id from plm_menu where menu_name in ('工作流', '可观测性', '调试工具', '系统信息')))
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);

-- source: V49__menu_full_button_perms.sql
insert into plm_role_menu (role_id, menu_id)
select r.id, m.id
from plm_role r, plm_menu m
where r.role_key = 'USER'
  and (m.menu_name = '总览'
    or m.menu_name = '资源'
    or m.parent_id = (select id from plm_menu where menu_name = '资源' and parent_id = 0)
    or m.parent_id in (select id from plm_menu where parent_id = (select id from plm_menu where menu_name = '资源' and parent_id = 0)))
  and not exists (select 1 from plm_role_menu prm where prm.role_id = r.id and prm.menu_id = m.id);
