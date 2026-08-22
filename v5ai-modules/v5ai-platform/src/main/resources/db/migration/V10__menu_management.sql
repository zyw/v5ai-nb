-- Phase 7 Menu management: tree-structure menus + role-menu assignment.
-- Menu is a global resource (like Role); pages are gated by role -> menu -> page visibility.

CREATE TABLE v5ai_menu (
    id         BIGSERIAL PRIMARY KEY,
    parent_id  BIGINT NULL REFERENCES v5ai_menu(id),
    code       VARCHAR(100) NOT NULL UNIQUE,          -- stable slug for seeds / frontend icon mapping
    name       VARCHAR(200) NOT NULL,
    type       VARCHAR(20)  NOT NULL DEFAULT 'MENU',  -- DIR | MENU
    route_name VARCHAR(100) UNIQUE,                   -- vue-router name (MENU only)
    path       VARCHAR(200),                          -- reference display path
    icon       VARCHAR(100),                          -- icon name mapped to a lucide component on the UI
    sort_order INTEGER      NOT NULL DEFAULT 0,
    visible    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_role_menu (
    role_key VARCHAR(100) NOT NULL REFERENCES v5ai_role(role_key),
    menu_id  BIGINT       NOT NULL REFERENCES v5ai_menu(id),
    PRIMARY KEY (role_key, menu_id)
);

-- ---- Seed menu tree mirroring the existing static navigation ----

INSERT INTO v5ai_menu (parent_id, code, name, type, route_name, path, icon, sort_order) VALUES
    (NULL, 'dashboard',          '总览',        'MENU', 'dashboard',          'dashboard',       'dashboard',          1),
    (NULL, 'g-resource',         '资源',        'DIR',  NULL,                 NULL,              'resource',           10),
    (NULL, 'g-system',           '系统管理',    'DIR',  NULL,                 NULL,              'system',             20),
    (NULL, 'g-ops',              '运维',        'DIR',  NULL,                 NULL,              'ops',                30);

INSERT INTO v5ai_menu (parent_id, code, name, type, route_name, path, icon, sort_order) VALUES
    ((SELECT id FROM v5ai_menu WHERE code = 'g-resource'), 'models',            '模型管理',   'MENU', 'models',            'models',            'models',            11),
    ((SELECT id FROM v5ai_menu WHERE code = 'g-resource'), 'agents',            'Agent 管理', 'MENU', 'agents',            'agents',            'agents',            12),
    ((SELECT id FROM v5ai_menu WHERE code = 'g-resource'), 'knowledge-bases',   '知识库管理', 'MENU', 'knowledge-bases',   'knowledge-bases',   'knowledge-bases',   13),
    ((SELECT id FROM v5ai_menu WHERE code = 'g-resource'), 'mcp-servers',       'MCP 管理',   'MENU', 'mcp-servers',       'mcp-servers',       'mcp-servers',       14),
    ((SELECT id FROM v5ai_menu WHERE code = 'g-resource'), 'skills',            'Skill 管理', 'MENU', 'skills',            'skills',            'skills',            15);

INSERT INTO v5ai_menu (parent_id, code, name, type, route_name, path, icon, sort_order) VALUES
    ((SELECT id FROM v5ai_menu WHERE code = 'g-system'), 'system-users',    '用户管理', 'MENU', 'system-users',    'system/users',    'users',   21),
    ((SELECT id FROM v5ai_menu WHERE code = 'g-system'), 'system-roles',    '角色管理', 'MENU', 'system-roles',    'system/roles',    'roles',   22),
    ((SELECT id FROM v5ai_menu WHERE code = 'g-system'), 'system-menus',    '菜单管理', 'MENU', 'system-menus',    'system/menus',    'menus',   23),
    ((SELECT id FROM v5ai_menu WHERE code = 'g-system'), 'system-tenants',  '租户管理', 'MENU', 'system-tenants',  'system/tenants',  'tenants', 24);

INSERT INTO v5ai_menu (parent_id, code, name, type, route_name, path, icon, sort_order) VALUES
    ((SELECT id FROM v5ai_menu WHERE code = 'g-ops'), 'observability', '可观测性', 'MENU', 'observability', 'observability', 'observability', 31),
    ((SELECT id FROM v5ai_menu WHERE code = 'g-ops'), 'chat',          '调试工具', 'MENU', 'chat',          'chat',          'chat',          32),
    ((SELECT id FROM v5ai_menu WHERE code = 'g-ops'), 'settings',      '系统信息', 'MENU', 'settings',      'settings',      'settings',      33);

-- ---- Seed role -> menu assignments ----

-- ADMIN sees every menu.
INSERT INTO v5ai_role_menu (role_key, menu_id)
SELECT 'ADMIN', id FROM v5ai_menu;

-- USER sees the dashboard plus the whole "资源" group (demonstrates finer-grained page access).
INSERT INTO v5ai_role_menu (role_key, menu_id)
SELECT 'USER', m.id FROM v5ai_menu m
WHERE m.code = 'dashboard'
   OR m.parent_id = (SELECT id FROM v5ai_menu WHERE code = 'g-resource');
