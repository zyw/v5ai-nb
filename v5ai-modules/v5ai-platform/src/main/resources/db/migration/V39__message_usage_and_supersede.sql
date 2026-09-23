-- ============================================================
-- V39-1：助手回答的用量与用时落库
--
-- 背景：门户在 LLM 回答下方展示「用量（token）/ 用时」，数据由 RUN_COMPLETED 的事件载荷带出。
--   但事件只在当次流式过程中存在，**刷新页面后历史回答就没有这两个数字了**——历史走的是
--   GET /chat/conversations/{id}，它只读 v5ai_message。所以把用量跟着回答一起存下来。
--
-- 为什么放在 v5ai_message 而不是 v5ai_run：
--   门户要展示的是「这条回答消耗了多少」，一条助手消息就对应一次运行；历史接口按消息查询，
--   存这里可以直接返回，不必再去 join v5ai_model_usage（那张表是明细账，用于可观测性统计）。
--   代价是同一次运行的用量在两处各有一份，但两处由同一次计算写入（见 PersistingAgentRuntime），
--   不会各估一套。
--
-- 三列都可空：用户消息本来就没有用量；本迁移之前的回答也不会有。
-- 三者要么都有值、要么都为 NULL，判空按 duration_ms 一列即可。
-- duration_ms 用 INT：毫秒计数下溢需要 24 天以上，远超单次运行的合理上限。
-- ============================================================

ALTER TABLE v5ai_message ADD COLUMN IF NOT EXISTS prompt_tokens INT;
ALTER TABLE v5ai_message ADD COLUMN IF NOT EXISTS completion_tokens INT;
ALTER TABLE v5ai_message ADD COLUMN IF NOT EXISTS duration_ms INT;

COMMENT ON COLUMN v5ai_message.prompt_tokens IS '该回答的输入 token 估算（按字符数 / 4，与模型用量表同口径）；仅助手消息有值';
COMMENT ON COLUMN v5ai_message.completion_tokens IS '该回答的输出 token 估算；仅助手消息有值';
COMMENT ON COLUMN v5ai_message.duration_ms IS '该次运行的服务端耗时（毫秒，RUN_STARTED 到收尾）；仅助手消息有值';

-- ============================================================
-- V39-2：消息「作废」标记（重新生成的软替换）
--
-- 背景：「重新生成」原本只是前端把旧回答从列表里丢掉重发一次，但服务端每轮都落库，
--   于是库里留下「作废的回答」与「重发的提问」，重开会话就能看到两条回答，
--   继续追问时模型也会把作废回答读进上下文。
--
-- 处理方式：**软作废而不是删除**。把被替换掉的消息打上 superseded_at，读取侧过滤掉，
--   数据全部保留（审计完整、可反悔、附件引用不断）。相比物理删除的额外好处是
--   「消息序列」与「已计费用量」始终能对上——用量表本来就不会因为重新生成而回滚。
--
-- 作废范围：**锚点提问之后**的全部消息（含被替换的回答，以及更晚的整轮问答）。
--   锚点提问本身保持有效——重新生成复用它、不新插提问行，所以历史里不会出现两条相同提问。
--
-- 读取侧需要过滤的位置只有两处（其余读路径都由它们间接覆盖）：
--   1) RuntimeMessageServiceImpl.findByConversationId（历史接口 / resume / 记忆回放）；
--   2) RuntimeMessageMapper.selectFirstUserMessages（会话列表的预览兜底）。
--   **刻意不过滤**：附件读取的推导式鉴权（v5ai_message_attachment → v5ai_message → api_key_id）——
--   那是「这把 Key 是否处置过该资源」的授权判断，不是展示查询，作废的消息同样能证明归属。
-- ============================================================

ALTER TABLE v5ai_message ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMPTZ;

COMMENT ON COLUMN v5ai_message.superseded_at IS '作废时间；非空表示该消息已被「重新生成」替换：不参与展示与记忆回放，但数据保留';

-- 会话消息目前只有 V4 补回的外键、没有索引，会话一多就是全表扫。
-- 读取侧恒带「未作废」条件，故用部分索引精确匹配该查询（conversation_id 过滤 + created_at,id 排序）。
CREATE INDEX IF NOT EXISTS idx_v5ai_message_conversation_active
    ON v5ai_message (conversation_id, created_at, id)
    WHERE superseded_at IS NULL;
