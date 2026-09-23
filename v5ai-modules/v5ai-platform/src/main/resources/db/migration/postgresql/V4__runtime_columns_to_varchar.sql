-- 将运行期表的 UUID 主键/外键列改为 VARCHAR：应用全程使用 String 类型的
-- conversationId / runId（API 允许任意字符串），PostgreSQL 不允许 uuid = character varying，
-- 导致会话/消息/run/事件读写失败。外键列参与约束时需先删约束、改类型、再重建。

ALTER TABLE v5ai_run_event DROP CONSTRAINT IF EXISTS v5ai_run_event_run_id_fkey;
ALTER TABLE v5ai_run DROP CONSTRAINT IF EXISTS v5ai_run_conversation_id_fkey;
ALTER TABLE v5ai_agent_state DROP CONSTRAINT IF EXISTS v5ai_agent_state_conversation_id_fkey;
ALTER TABLE v5ai_message DROP CONSTRAINT IF EXISTS v5ai_message_conversation_id_fkey;

ALTER TABLE v5ai_conversation ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE v5ai_message ALTER COLUMN conversation_id TYPE VARCHAR(36) USING conversation_id::text;
ALTER TABLE v5ai_run ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE v5ai_run ALTER COLUMN conversation_id TYPE VARCHAR(36) USING conversation_id::text;
ALTER TABLE v5ai_run_event ALTER COLUMN run_id TYPE VARCHAR(36) USING run_id::text;
ALTER TABLE v5ai_agent_state ALTER COLUMN conversation_id TYPE VARCHAR(36) USING conversation_id::text;

ALTER TABLE v5ai_message ADD CONSTRAINT v5ai_message_conversation_id_fkey
    FOREIGN KEY (conversation_id) REFERENCES v5ai_conversation(id);
ALTER TABLE v5ai_agent_state ADD CONSTRAINT v5ai_agent_state_conversation_id_fkey
    FOREIGN KEY (conversation_id) REFERENCES v5ai_conversation(id);
ALTER TABLE v5ai_run ADD CONSTRAINT v5ai_run_conversation_id_fkey
    FOREIGN KEY (conversation_id) REFERENCES v5ai_conversation(id);
ALTER TABLE v5ai_run_event ADD CONSTRAINT v5ai_run_event_run_id_fkey
    FOREIGN KEY (run_id) REFERENCES v5ai_run(id);
