COMMENT ON TABLE v5ai_model IS '模型配置：id 为平台身份，同一 provider 下允许重复 model_key；凭据加密存储，config 为扩展参数 JSON';
COMMENT ON COLUMN v5ai_model_usage.model_id IS '平台模型配置 ID；历史记录可能为空，用于区分重复 model_key';
