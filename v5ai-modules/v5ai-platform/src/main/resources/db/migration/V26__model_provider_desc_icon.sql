-- 模型供应商：新增描述 / LOGO 图标 URL；移除 provider_type（运行时已改为按 providerKey + model.adapterKey 判别适配器）。
ALTER TABLE v5ai_model_provider ADD COLUMN IF NOT EXISTS description TEXT;
comment on column v5ai_model_provider.description is '提供商描述';

ALTER TABLE v5ai_model_provider ADD COLUMN IF NOT EXISTS icon_url VARCHAR(500);
comment on column v5ai_model_provider.icon_url is 'LOGO图标URL';

ALTER TABLE v5ai_model_provider DROP COLUMN IF EXISTS provider_type;
