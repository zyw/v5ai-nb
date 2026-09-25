ALTER TABLE v5ai_workflow ADD COLUMN draft_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE v5ai_workflow_version (
    id BIGSERIAL PRIMARY KEY,
    workflow_key VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL,
    definition TEXT NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    change_summary TEXT,
    published_by BIGINT,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT v5ai_workflow_version_key_version UNIQUE (workflow_key, version)
);
CREATE INDEX idx_v5ai_workflow_version_key ON v5ai_workflow_version(workflow_key, version DESC);
INSERT INTO v5ai_workflow_version (workflow_key, version, definition, schema_version, published_at, created_at)
SELECT workflow_key, published_version, published_definition, 1,
       COALESCE(published_at, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP
FROM v5ai_workflow
WHERE published_definition IS NOT NULL AND published_version IS NOT NULL
ON CONFLICT (workflow_key, version) DO NOTHING;

ALTER TABLE v5ai_workflow_run
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN draft_revision BIGINT,
    ADD COLUMN definition_snapshot TEXT;

ALTER TABLE v5ai_workflow_node_run
    ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN duration_ms BIGINT,
    ADD COLUMN error_code VARCHAR(100),
    ADD COLUMN executor_type VARCHAR(100),
    ADD COLUMN agent_run_id VARCHAR(36);

COMMENT ON TABLE v5ai_workflow_version IS '工作流不可变发布版本快照';
COMMENT ON COLUMN v5ai_workflow_version.definition IS '工作流定义 JSON 快照';
COMMENT ON COLUMN v5ai_workflow_run.source IS '运行来源（PUBLISHED / DRAFT_TEST）';
COMMENT ON COLUMN v5ai_workflow_run.definition_snapshot IS '草稿测试运行时的定义快照';
