-- Phase 1 修正：v5ai_model_provider 缺少 provider_type 列（实体已映射但建表语句缺失）。
-- 使用 IF NOT EXISTS 兼容已应用 V1 的数据库与全新数据库。
ALTER TABLE v5ai_model_provider ADD COLUMN IF NOT EXISTS provider_type VARCHAR(40);
