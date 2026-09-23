-- Allow multiple platform configurations for the same provider/upstream model key.
ALTER TABLE v5ai_model
    DROP CONSTRAINT IF EXISTS v5ai_model_provider_id_model_key_key;

CREATE INDEX IF NOT EXISTS idx_v5ai_model_provider_key
    ON v5ai_model(provider_id, model_key);

-- Historical usage rows cannot be safely backfilled because the old table did not
-- retain provider_id. New rows identify the platform model by ID.
ALTER TABLE v5ai_model_usage
    ADD COLUMN IF NOT EXISTS model_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_v5ai_model_usage_model
    ON v5ai_model_usage(model_id, created_at);


COMMENT ON TABLE v5ai_model IS '模型配置：id 为平台身份，同一 provider 下允许重复 model_key；凭据加密存储，config 为扩展参数 JSON';
COMMENT ON COLUMN v5ai_model_usage.model_id IS '平台模型配置 ID；历史记录可能为空，用于区分重复 model_key';
