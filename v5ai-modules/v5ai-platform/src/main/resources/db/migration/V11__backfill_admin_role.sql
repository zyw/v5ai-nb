-- Phase 7.1 Backfill: ensure the built-in admin account carries the ADMIN role.
-- Older installs seeded the admin user before RBAC existed (Phase 1), leaving it
-- role-less, which now yields an empty menu set and locks it out of the console.
INSERT INTO v5ai_user_role (user_id, role_key)
SELECT u.id, 'ADMIN'
FROM v5ai_user u
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM v5ai_user_role ur
      WHERE ur.user_id = u.id AND ur.role_key = 'ADMIN'
  );
