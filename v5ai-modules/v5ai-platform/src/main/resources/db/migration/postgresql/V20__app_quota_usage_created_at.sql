-- V20: v5ai_app_quota / v5ai_app_usage 补充 created_at 创建时间字段

ALTER TABLE v5ai_app_quota ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE v5ai_app_usage ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
