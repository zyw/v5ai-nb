-- V19: 运维日志/登录日志表更名 plm_* 前缀 + 新增 client_key（客户端Key）
-- 说明：PG 更名表时自动同步重命名索引/约束；列注释随列保留。

ALTER TABLE v5ai_oper_log ADD COLUMN client_key VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE v5ai_oper_log RENAME TO plm_oper_log;
COMMENT ON COLUMN plm_oper_log.client_key IS '客户端Key';

ALTER TABLE v5ai_login_info ADD COLUMN client_key VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE v5ai_login_info RENAME TO plm_login_info;
COMMENT ON COLUMN plm_login_info.client_key IS '客户端Key';
