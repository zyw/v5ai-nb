-- 模型 config 列从 jsonb 降级为 text。
-- 原因：v5ai_model.config 是项目里唯一的 jsonb 列，其它 JSON 形态列均为 text；
-- config 从未被 jsonb 路径查询、也未被读进运行时，jsonb 的归一化反而破坏编辑器回显保真；
-- 且实体字段为 String（MyBatis-Plus 按 varchar 绑定）导致 jsonb 写入时报
-- "column config is of type jsonb but expression is of type character varying"。
alter table v5ai_model
    alter column config type text using config::text;