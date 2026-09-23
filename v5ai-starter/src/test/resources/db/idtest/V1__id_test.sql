CREATE TABLE v5ai_model_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    icon_url VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
