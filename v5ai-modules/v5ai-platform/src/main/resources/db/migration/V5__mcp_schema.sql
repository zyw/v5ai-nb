-- MCP Phase 3: MCP server registry, cached tool metadata, application bindings and tool call audit.

CREATE TABLE v5ai_mcp_server (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    transport_type VARCHAR(30) NOT NULL,
    endpoint VARCHAR(1000),
    args_json TEXT,
    headers_ciphertext TEXT,
    env_ciphertext TEXT,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_test_status VARCHAR(30),
    last_test_message TEXT,
    last_tested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_mcp_tool (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES v5ai_mcp_server(id),
    tool_name VARCHAR(300) NOT NULL,
    description TEXT,
    input_schema_json TEXT,
    read_only BOOLEAN NOT NULL DEFAULT FALSE,
    permission VARCHAR(30) NOT NULL DEFAULT 'ALLOW',
    last_discovered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(server_id, tool_name)
);
CREATE INDEX idx_v5ai_mcp_tool_server ON v5ai_mcp_tool(server_id);

CREATE TABLE v5ai_application_mcp (
    app_key VARCHAR(100) NOT NULL,
    mcp_server_id BIGINT NOT NULL REFERENCES v5ai_mcp_server(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(app_key, mcp_server_id)
);

CREATE TABLE v5ai_mcp_tool_call (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    server_id BIGINT NOT NULL REFERENCES v5ai_mcp_server(id),
    server_name VARCHAR(200) NOT NULL,
    tool_name VARCHAR(300) NOT NULL,
    arguments_summary TEXT,
    decision VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    duration_ms BIGINT,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_v5ai_mcp_tool_call_run ON v5ai_mcp_tool_call(run_id);
CREATE INDEX idx_v5ai_mcp_tool_call_server ON v5ai_mcp_tool_call(server_id);
