-- Phase 5.5 Agent rename: Application → Agent across schema.
-- Tables and columns are renamed; app_key becomes agent_key everywhere.

-- ---- Rename agent-owned tables ----
ALTER TABLE v5ai_application RENAME TO v5ai_agent;
ALTER TABLE v5ai_application_version RENAME TO v5ai_agent_version;
ALTER TABLE v5ai_application_model RENAME TO v5ai_agent_model;
ALTER TABLE v5ai_application_knowledge RENAME TO v5ai_agent_knowledge;
ALTER TABLE v5ai_application_mcp RENAME TO v5ai_agent_mcp;
ALTER TABLE v5ai_application_skill RENAME TO v5ai_agent_skill;

-- ---- Rename columns ----
ALTER TABLE v5ai_agent RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_agent_version RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_agent_model RENAME COLUMN application_id TO agent_id;
ALTER TABLE v5ai_agent_knowledge RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_agent_mcp RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_agent_skill RENAME COLUMN app_key TO agent_key;

ALTER TABLE v5ai_api_key RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_conversation RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_message RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_run RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_app_quota RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_app_usage RENAME COLUMN app_key TO agent_key;
ALTER TABLE v5ai_model_usage RENAME COLUMN app_key TO agent_key;

-- ---- Agent system prompt ----
ALTER TABLE v5ai_agent ADD COLUMN system_prompt TEXT;
