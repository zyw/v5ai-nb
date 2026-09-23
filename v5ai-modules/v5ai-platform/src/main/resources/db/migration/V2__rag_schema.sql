-- RAG Phase 2: knowledge bases, documents, chunks (pgvector), tasks and application bindings.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE v5ai_knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL REFERENCES v5ai_knowledge_base(id),
    title VARCHAR(300) NOT NULL,
    file_type VARCHAR(30) NOT NULL,
    source VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    content BYTEA,
    parsed_text TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL REFERENCES v5ai_knowledge_document(id),
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_v5ai_knowledge_chunk_document ON v5ai_knowledge_chunk(document_id);
CREATE INDEX idx_v5ai_knowledge_chunk_embedding
    ON v5ai_knowledge_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE TABLE v5ai_knowledge_task (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL REFERENCES v5ai_knowledge_document(id),
    task_type VARCHAR(30) NOT NULL DEFAULT 'PARSE_AND_INDEX',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_v5ai_knowledge_task_status ON v5ai_knowledge_task(status, created_at);

CREATE TABLE v5ai_application_knowledge (
    app_key VARCHAR(100) NOT NULL,
    knowledge_base_id BIGINT NOT NULL REFERENCES v5ai_knowledge_base(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(app_key, knowledge_base_id)
);
