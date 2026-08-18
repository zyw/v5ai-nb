-- Phase: 角色表 name → role_name，新增 data_scope 数据范围
-- data_scope：1全部 2自定 3本部门 4本部门及以下 5仅本人 6部门及以下或本人

ALTER TABLE v5ai_role RENAME COLUMN name TO role_name;
ALTER TABLE v5ai_role ADD COLUMN data_scope VARCHAR(1) NOT NULL DEFAULT '1';

COMMENT ON COLUMN v5ai_role.role_name   IS '角色名称';
COMMENT ON COLUMN v5ai_role.data_scope  IS '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限 5：仅本人数据权限 6：部门及以下或本人数据权限）';
