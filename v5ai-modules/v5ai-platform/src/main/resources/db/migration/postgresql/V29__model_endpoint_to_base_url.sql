-- v5ai_model.endpoint 重命名为 base_url：统一「API 基础地址」命名，
-- 与凭据 JSON 的 baseUrl 键、运行时 AgentModelCredentialConfig 解析键保持一致。
alter table v5ai_model
    rename column endpoint to base_url;

comment on column v5ai_model.base_url is 'API 基础地址(与凭据 JSON 的 baseUrl 键一致, 如 https://api.deepseek.com/v1)';