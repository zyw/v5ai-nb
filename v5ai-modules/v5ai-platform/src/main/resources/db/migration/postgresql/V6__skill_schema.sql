-- Skill Phase 4: skills, versions, files (workspace injection content) and application bindings.

CREATE TABLE v5ai_skill (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    -- 当前生效（运行时注入）的已发布版本；不建外键以免与 v5ai_skill_version 循环引用
    current_version_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE v5ai_skill_version (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES v5ai_skill(id),
    version BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    description TEXT,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, version)
);
CREATE INDEX idx_v5ai_skill_version_skill ON v5ai_skill_version(skill_id);

CREATE TABLE v5ai_skill_file (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES v5ai_skill(id),
    version_id BIGINT NOT NULL REFERENCES v5ai_skill_version(id),
    file_path VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(version_id, file_path)
);
CREATE INDEX idx_v5ai_skill_file_version ON v5ai_skill_file(version_id);

CREATE TABLE v5ai_application_skill (
    app_key VARCHAR(100) NOT NULL,
    skill_id BIGINT NOT NULL REFERENCES v5ai_skill(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(app_key, skill_id)
);
