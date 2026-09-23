-- ============================================================
-- V35：API Key 归属用户 + 多 Agent 绑定
--
-- 背景：Phase 1 的 v5ai_api_key 以 agent_key 作为归属（UNIQUE(agent_key) + upsert 覆盖），
--   一个 Agent 只能有一把 Key、一把 Key 只能访问一个 Agent，无法表达
--   「一把 Key 访问多个已发布 Agent」。
--
-- 本次改动：
--   1) v5ai_api_key 表更名为 v5ai_api_keys，并增加 user_id（归属用户）、name（Key 名称）、
--      tracking_id（跟踪 ID）、last_used_at（最新使用时间）、updated_at（修改时间），删除 agent_key；
--   2) 新增 v5ai_api_keys_agent 保存「Key 可访问的 Agent」多对多关系；
--   3) 历史 Key 作废删除——库内只有 BCrypt 哈希，无法还原明文以推导 tracking_id，
--      既有 Key 无法继续鉴权（已确认口径：直接删除，使用方需重新签发）；
--   4) 补「API Key 管理」菜单与 ADMIN/USER 授权（沿 V18「资源组菜单全部授权」的口径）。
--
-- tracking_id 说明：明文 Key 形如 v5ai_<32 位随机串>，取第 6–17 位（12 位）明文入库为
--   tracking_id，运行时据此在唯一索引上定位密钥行，再做 BCrypt 全串校验
--   （BCrypt 是单向哈希，无法用明文反查行；62^12≈3.2e21 空间，碰撞可忽略）。
--
-- 历史迁移只增不改。
-- ============================================================

-- ----------- 1. 修改v5ai_api_key表名为v5ai_api_keys-----------------------------

ALTER TABLE v5ai_api_key RENAME TO v5ai_api_keys;

-- ---------- 2. v5ai_api_keys 改造 ----------

ALTER TABLE v5ai_api_keys ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE v5ai_api_keys ADD COLUMN IF NOT EXISTS name VARCHAR(100);
ALTER TABLE v5ai_api_keys ADD COLUMN IF NOT EXISTS tracking_id VARCHAR(32);
ALTER TABLE v5ai_api_keys ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMPTZ;
ALTER TABLE v5ai_api_keys ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN v5ai_api_keys.user_id IS 'Key 归属用户（plm_user.id）';
COMMENT ON COLUMN v5ai_api_keys.name IS 'Key 名称（页面主标识）';
COMMENT ON COLUMN v5ai_api_keys.tracking_id IS '跟踪 ID：明文 Key 第 6-17 位，运行时唯一索引定位密钥行，日志/审计可追溯';
COMMENT ON COLUMN v5ai_api_keys.last_used_at IS '最新使用时间（鉴权成功时刷新）';
COMMENT ON COLUMN v5ai_api_keys.updated_at IS '修改时间';

-- 历史 Key 作废（撤销既有凭据：明文不可恢复，重签后由新页面重新绑定 Agent）
DELETE FROM v5ai_api_keys;

ALTER TABLE v5ai_api_keys DROP COLUMN IF EXISTS agent_key;
ALTER TABLE v5ai_api_keys ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE v5ai_api_keys ALTER COLUMN name SET NOT NULL;
ALTER TABLE v5ai_api_keys ALTER COLUMN tracking_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_v5ai_api_keys_tracking_id ON v5ai_api_keys (tracking_id);
CREATE INDEX IF NOT EXISTS idx_v5ai_api_keys_user_id ON v5ai_api_keys (user_id);

-- ---------- 3. v5ai_api_keys_agent（Key 可访问的 Agent） ----------

CREATE TABLE IF NOT EXISTS v5ai_api_keys_agent (
    id         BIGSERIAL PRIMARY KEY,
    api_key_id BIGINT       NOT NULL REFERENCES v5ai_api_keys (id) ON DELETE CASCADE,
    agent_key  VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_v5ai_api_keys_agent UNIQUE (api_key_id, agent_key)
);

CREATE INDEX IF NOT EXISTS idx_v5ai_api_keys_agent_agent_key ON v5ai_api_keys_agent (agent_key);

COMMENT ON TABLE v5ai_api_keys_agent IS 'API Key ↔ Agent 绑定：某把 Key 可以访问哪些 Agent（仅已发布 Agent）';
COMMENT ON COLUMN v5ai_api_keys_agent.api_key_id IS 'v5ai_api_keys.id（Key 删除时级联删除绑定）';
COMMENT ON COLUMN v5ai_api_keys_agent.agent_key IS 'v5ai_agent.agent_key（不建跨模块外键，删除 Agent 时显式清理）';

-- ---------- 4. 菜单：资源 / API Key 管理（含按钮权限） ----------

INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'API Keys', id, 18, 'api-keys', 'resource/api-keys/index', 'C', '0', '0', 'apiKeys:keys:list', 'api-keys'
FROM plm_menu
WHERE menu_name = '资源'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE menu_name = 'API Keys');

INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'Key 查询', id, 1, '', '', 'F', '0', '0', 'apiKeys:keys:query', '#'
FROM plm_menu
WHERE menu_name = 'API Keys'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE perms = 'apiKeys:keys:query');

INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'Key 新增', id, 2, '', '', 'F', '0', '0', 'apiKeys:keys:add', '#'
FROM plm_menu
WHERE menu_name = 'API Keys'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE perms = 'apiKeys:keys:add');

INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'Key 修改', id, 3, '', '', 'F', '0', '0', 'apiKeys:keys:edit', '#'
FROM plm_menu
WHERE menu_name = 'API Keys'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE perms = 'apiKeys:keys:edit');

INSERT INTO plm_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 'Key 删除', id, 4, '', '', 'F', '0', '0', 'apiKeys:keys:remove', '#'
FROM plm_menu
WHERE menu_name = 'API Keys'
  AND NOT EXISTS (SELECT 1 FROM plm_menu WHERE perms = 'apiKeys:keys:remove');

INSERT INTO plm_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM plm_role r, plm_menu m
WHERE r.role_key IN ('ADMIN', 'USER')
  AND m.menu_name IN ('API Keys', 'Key 查询', 'Key 新增', 'Key 修改', 'Key 删除')
  AND NOT EXISTS (SELECT 1 FROM plm_role_menu prm WHERE prm.role_id = r.id AND prm.menu_id = m.id);