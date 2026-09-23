
-- ============================================================
-- V41：同会话历史摘要（窗口外内容的滚动压缩）
--
-- 背景：同会话历史此前**没有条数/字符上限**，长会话会把完整消息列表原样发给模型，
--   直到某次运行直接撞上模型的上下文窗口（表现为整轮 RUN_FAILED）。
--   本迁移配合「历史窗口」（v5ai.agentscope.history，默认 40 条 / 8000 字符）落地：
--   窗口内的历史逐条回放，窗口外的历史压缩成一段摘要继续参与上下文，
--   既不丢信息、也不会随会话长度无限膨胀。
--
-- 为什么摘要要落库而不是放内存/交给 SDK：
--   1) 每次运行的历史都是按 v5ai_message 重建的，进程内状态活不过下一次运行；
--   2) 摘要必须能被「重新生成」作废（见 covered_until_message_id 的失效规则）；
--   3) 摘要是租户数据——它派生自 v5ai_conversation，随会话删除而删除。
--
-- covered_until_message_id 是**水位**：id ≤ 该值的消息都已被摘要覆盖。
--   写入侧只在「新水位更高」时覆盖旧摘要（ON CONFLICT ... WHERE），
--   因此并发触发的两次摘要不会把结果改旧；读取侧按它决定「从哪条之后继续压」。
-- ============================================================

CREATE TABLE IF NOT EXISTS v5ai_conversation_summary (
    conversation_id          VARCHAR(36) PRIMARY KEY REFERENCES v5ai_conversation (id) ON DELETE CASCADE,
    summary                  TEXT        NOT NULL,
    covered_until_message_id BIGINT      NOT NULL,
    covered_messages         INT         NOT NULL DEFAULT 0,
    model_id                 BIGINT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE v5ai_conversation_summary IS '会话历史摘要：历史窗口之外的旧内容压缩成一段文本继续参与上下文；一会话一行（滚动覆盖）';
COMMENT ON COLUMN v5ai_conversation_summary.summary IS '摘要正文（模型生成，长度上限见 v5ai.portal.conversation-summary.max-summary-chars）';
COMMENT ON COLUMN v5ai_conversation_summary.covered_until_message_id IS '摘要覆盖水位：id ≤ 该值的消息都已并入摘要（重新生成锚点落在其中时整条摘要作废）';
COMMENT ON COLUMN v5ai_conversation_summary.covered_messages IS '已并入摘要的消息条数（观测用）';
COMMENT ON COLUMN v5ai_conversation_summary.model_id IS '生成该摘要的模型 id（审计用；配置了固定摘要模型时与 Agent 绑定模型不同）';

-- ============================================================
-- 同一批：修正 V39 写下的用量列注释——token 不再只按字符估算
--   现在是「模型回报的真实值优先（AgentScope 的 ChatUsage / ModelCallEndEvent），
--   服务端未回报时回退为平台估算（CJK 按 1 字 1 token，其余 4 字符 1 token）」。
-- ============================================================

COMMENT ON COLUMN v5ai_message.prompt_tokens IS '该回答的输入 token：模型回报的真实值优先，未回报时回退为平台字符估算；仅助手消息有值';
COMMENT ON COLUMN v5ai_message.completion_tokens IS '该回答的输出 token：口径同上；仅助手消息有值';
