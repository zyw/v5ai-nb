-- Phase 8 Workflow: DAG node/variable/condition orchestration + run records.

CREATE TABLE v5ai_workflow (
    id                   BIGSERIAL PRIMARY KEY,
    workflow_key         VARCHAR(100) NOT NULL UNIQUE,
    name                 VARCHAR(200) NOT NULL,
    description          TEXT,
    status               VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT|PUBLISHED|DISABLED
    draft_definition     TEXT,          -- JSON: { nodes, edges }
    published_definition TEXT,          -- JSON: { nodes, edges }
    published_version    BIGINT,
    published_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_workflow_run (
    run_id           VARCHAR(36) PRIMARY KEY,
    workflow_key     VARCHAR(100) NOT NULL,
    workflow_version BIGINT,
    status           VARCHAR(30) NOT NULL DEFAULT 'RUNNING',  -- RUNNING|SUCCEEDED|FAILED
    inputs           TEXT,          -- JSON
    outputs          TEXT,          -- JSON
    error            TEXT,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_v5ai_workflow_run_key ON v5ai_workflow_run(workflow_key, created_at);

CREATE TABLE v5ai_workflow_node_run (
    id          BIGSERIAL PRIMARY KEY,
    run_id      VARCHAR(36) NOT NULL REFERENCES v5ai_workflow_run(run_id),
    node_id     VARCHAR(100) NOT NULL,
    node_type   VARCHAR(30)  NOT NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'PENDING',  -- PENDING|RUNNING|SUCCEEDED|FAILED|SKIPPED
    inputs      TEXT,          -- JSON
    outputs     TEXT,          -- JSON
    error       TEXT,
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_v5ai_workflow_node_run_run ON v5ai_workflow_node_run(run_id);

-- ---- Seed menu: 工作流 (under 资源 group) + ADMIN grant ----

INSERT INTO v5ai_menu (parent_id, code, name, type, route_name, path, icon, sort_order) VALUES
    ((SELECT id FROM v5ai_menu WHERE code = 'g-resource'), 'workflows', '工作流', 'MENU', 'workflows', 'workflows', 'workflows', 16);

INSERT INTO v5ai_role_menu (role_key, menu_id)
SELECT 'ADMIN', id FROM v5ai_menu WHERE code = 'workflows';
