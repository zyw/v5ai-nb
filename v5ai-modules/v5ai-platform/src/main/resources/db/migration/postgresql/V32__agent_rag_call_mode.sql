-- RAG 调用方式：1=智能调用（LLM 决定是否检索）2=强制调用（每次必检索）。
-- 默认 2 以兼容存量 Agent 的历史行为（ragEnabled=true 即每次必检索）。
ALTER TABLE v5ai_agent ADD COLUMN rag_call_mode SMALLINT NOT NULL DEFAULT 2;

COMMENT ON COLUMN v5ai_agent.rag_call_mode IS 'RAG调用方式: 1=智能调用 2=强制调用';