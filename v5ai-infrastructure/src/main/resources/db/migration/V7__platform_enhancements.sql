-- Phase 5 Platform enhancements: tenants, RBAC, quotas, usage, audit.

-- ---- Multi-tenancy + RBAC ----

CREATE TABLE v5ai_tenant (
    id BIGSERIAL PRIMARY KEY,
    tenant_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE v5ai_user ADD COLUMN tenant_id BIGINT;
ALTER TABLE v5ai_user ADD COLUMN display_name VARCHAR(200);
CREATE INDEX idx_v5ai_user_tenant ON v5ai_user(tenant_id);

CREATE TABLE v5ai_role (
    role_key VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL
);

CREATE TABLE v5ai_user_role (
    user_id BIGINT NOT NULL REFERENCES v5ai_user(id),
    role_key VARCHAR(100) NOT NULL REFERENCES v5ai_role(role_key),
    PRIMARY KEY(user_id, role_key)
);

INSERT INTO v5ai_tenant (tenant_key, name) VALUES ('default', 'Default Tenant');
INSERT INTO v5ai_role (role_key, name) VALUES ('admin', 'Administrator'), ('user', 'User');

-- ---- Application quotas & usage (keyed by appKey) ----

CREATE TABLE v5ai_app_quota (
    app_key VARCHAR(100) PRIMARY KEY,
    -- 0 表示不限
    daily_model_calls INTEGER NOT NULL DEFAULT 0,
    daily_tokens BIGINT NOT NULL DEFAULT 0,
    rate_per_minute INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_app_usage (
    app_key VARCHAR(100) NOT NULL,
    usage_date DATE NOT NULL,
    model_calls INTEGER NOT NULL DEFAULT 0,
    tokens BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(app_key, usage_date)
);

-- ---- Model usage detail (observability) ----

CREATE TABLE v5ai_model_usage (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64),
    app_key VARCHAR(100),
    model_key VARCHAR(200),
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_v5ai_model_usage_app ON v5ai_model_usage(app_key, created_at);
CREATE INDEX idx_v5ai_model_usage_created ON v5ai_model_usage(created_at);

-- ---- Audit log ----

CREATE TABLE v5ai_audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    actor VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100),
    target_id VARCHAR(100),
    detail TEXT,
    ip VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_v5ai_audit_log_created ON v5ai_audit_log(created_at);
CREATE INDEX idx_v5ai_audit_log_actor ON v5ai_audit_log(actor);
