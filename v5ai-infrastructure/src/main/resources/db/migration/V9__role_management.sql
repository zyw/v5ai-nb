-- Phase 6 Role management: extend v5ai_role with description/builtin/timestamps
-- and normalize role keys to uppercase (matches RoleKey enum and the RBAC hasRole("ADMIN") check).

-- Normalize any lowercase seed keys (V7 seeded 'admin'/'user') to uppercase.
UPDATE v5ai_role SET role_key = UPPER(role_key) WHERE role_key <> UPPER(role_key);
UPDATE v5ai_user_role SET role_key = UPPER(role_key) WHERE role_key <> UPPER(role_key);

ALTER TABLE v5ai_role ADD COLUMN description TEXT;
ALTER TABLE v5ai_role ADD COLUMN builtin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE v5ai_role ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE v5ai_role ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE v5ai_role SET builtin = TRUE,
    description = '平台管理员：可管理租户、用户与全部资源'
    WHERE role_key = 'ADMIN';
UPDATE v5ai_role SET builtin = TRUE,
    description = '普通用户：仅允许常规管理操作'
    WHERE role_key = 'USER';
