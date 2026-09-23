CREATE TABLE v5ai_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_api_key (
    id BIGSERIAL PRIMARY KEY,
    app_key VARCHAR(100) NOT NULL UNIQUE,
    secret_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_model_provider (
    id BIGSERIAL PRIMARY KEY,
    provider_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_model (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES v5ai_model_provider(id),
    model_key VARCHAR(200) NOT NULL,
    model_type VARCHAR(40) NOT NULL,
    endpoint VARCHAR(500),
    credentials_ciphertext TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider_id, model_key)
);

CREATE TABLE v5ai_application (
    id BIGSERIAL PRIMARY KEY,
    app_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    model_id BIGINT REFERENCES v5ai_model(id),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    published_version BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_application_version (
    id BIGSERIAL PRIMARY KEY,
    app_key VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL,
    snapshot_json TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(app_key, version)
);

CREATE TABLE v5ai_application_model (
    application_id BIGINT NOT NULL REFERENCES v5ai_application(id),
    model_id BIGINT NOT NULL REFERENCES v5ai_model(id),
    PRIMARY KEY(application_id, model_id)
);

CREATE TABLE v5ai_conversation (
    id UUID PRIMARY KEY,
    app_key VARCHAR(100) NOT NULL,
    user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES v5ai_conversation(id),
    app_key VARCHAR(100),
    role VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_agent_state (
    conversation_id UUID PRIMARY KEY REFERENCES v5ai_conversation(id),
    state_json TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_run (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES v5ai_conversation(id),
    app_key VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE TABLE v5ai_run_event (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES v5ai_run(id),
    event_type VARCHAR(50) NOT NULL,
    payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
