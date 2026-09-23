-- Phase 9 Agent 展示与能力开关字段
-- avatar/greeting/preset_questions：展示配置（字符串可空）
-- memory/mcp/skill/web_search/rag 开关：布尔能力开关（默认关闭）

ALTER TABLE v5ai_agent
    ADD COLUMN avatar              VARCHAR(512),
    ADD COLUMN greeting            TEXT,
    ADD COLUMN preset_questions    TEXT,
    ADD COLUMN memory_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mcp_enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN skill_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN web_search_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN rag_enabled         BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN v5ai_agent.avatar             IS '头像URL';
COMMENT ON COLUMN v5ai_agent.greeting           IS '欢迎语';
COMMENT ON COLUMN v5ai_agent.preset_questions   IS '预设问题列表（JSON数组字符串）';
COMMENT ON COLUMN v5ai_agent.memory_enabled     IS '是否启用记忆库';
COMMENT ON COLUMN v5ai_agent.mcp_enabled        IS '是否启用MCP';
COMMENT ON COLUMN v5ai_agent.skill_enabled      IS '是否启用Skill';
COMMENT ON COLUMN v5ai_agent.web_search_enabled IS '是否启用联网搜索';
COMMENT ON COLUMN v5ai_agent.rag_enabled        IS '是否启用RAG';
