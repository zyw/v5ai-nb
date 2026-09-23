-- ============================================================
-- V38-1：会话归属（API Key）+ 命名 + 归档
--
-- 背景：独立对话门户（v5ai-ui-chat）需要把会话变成服务端一等对象（列表/改名/归档）。
--   归属主体定为 API Key 而非用户——只有 Key 同时满足「调用方可识别（每个运行请求都带 Key）、
--   可分发、可吊销」；绑定关系见 docs/adr/0006-api-key-scoped-conversations.md。
--   v5ai_conversation.user_id 只作展示与审计冗余，不参与任何归属判定（当前恒为 NULL）。
--
-- api_key_id 为 NULL 表示「不属于任何 Key」——调试入口（Sa-Token）产生的会话即此类，
--   因而天然不出现在门户列表里；这是刻意取舍，不是遗漏。
--
-- archived_at 是软收尾态：非空即已归档，列表默认隐藏且**禁止继续对话**，
--   但消息与运行记录全部保留，可取消归档恢复（见 CONTEXT.md「归档」）。
-- ============================================================

ALTER TABLE v5ai_conversation ADD COLUMN IF NOT EXISTS api_key_id BIGINT;
ALTER TABLE v5ai_conversation ADD COLUMN IF NOT EXISTS name VARCHAR(100);
ALTER TABLE v5ai_conversation ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

COMMENT ON COLUMN v5ai_conversation.api_key_id IS '会话归属的 API Key（门户侧租户边界）；调试入口产生的会话为 NULL，不属于任何 Key';
COMMENT ON COLUMN v5ai_conversation.name IS '会话名称（调用方可改，1-100 字符）；NULL 表示未命名';
COMMENT ON COLUMN v5ai_conversation.archived_at IS '归档时间；非空表示已归档：列表默认隐藏且禁止继续对话';

CREATE INDEX IF NOT EXISTS idx_v5ai_conversation_api_key_agent ON v5ai_conversation (api_key_id, agent_key);

-- ============================================================
-- V38-2：消息附件关联（图片）
--
-- 附件先上传为资源（plm_resource，biz_type=ATTACHMENT），再以 resource_id 随消息提交；
-- 本表只承载「哪条消息带了哪个资源、第几张开」，资源字节本身仍在 plm_resource/对象存储。
-- ordinal 让同一条消息的多张图片顺序稳定（回放时模型看到的顺序与用户提交顺序一致）。
--
-- resource_id 上的索引服务于附件读取的推导式鉴权（ADR 0006 Q20）：
--   资源可在「本 Key 的某条消息引用了它」时被读取，该判定从本表反查。
-- ============================================================

CREATE TABLE IF NOT EXISTS v5ai_message_attachment (
    id          BIGSERIAL PRIMARY KEY,
    message_id  BIGINT NOT NULL REFERENCES v5ai_message (id) ON DELETE CASCADE,
    resource_id BIGINT NOT NULL REFERENCES plm_resource (id) ON DELETE CASCADE,
    type        VARCHAR(32) NOT NULL,
    ordinal     INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_v5ai_message_attachment_msg_ord UNIQUE (message_id, ordinal)
);
CREATE INDEX IF NOT EXISTS idx_v5ai_message_attachment_resource ON v5ai_message_attachment (resource_id);
COMMENT ON TABLE v5ai_message_attachment IS '消息携带的附件（图片）：resource_id 指向 plm_resource 中的 ATTACHMENT 资源';
COMMENT ON COLUMN v5ai_message_attachment.type IS '附件类型，当前只有 IMAGE';
COMMENT ON COLUMN v5ai_message_attachment.ordinal IS '同一条消息内附件的展示/回放顺序（从 0 起）';
