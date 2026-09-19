-- ============================================================
-- V42：助手消息的思考过程（reasoning）与保留列（metadata）
--
-- 背景：门户在实时流里能折叠展示模型思考（REASONING_DELTA），但它从不落库——
--   刷新或重进会话，思考就消失了。本迁移把它落到**所属助手消息**上：
--     - 与正文同生共死：作废（superseded_at）、级联删除、附件归属一并复用既有机制；
--     - 仅助手消息有值；被停止或失败时连同半截一并保留；NULL = 没有思考或 V42 之前的数据；
--     - 思考**不回放给模型**：不进上下文、不占历史窗口预算、也不进会话摘要
--       （见 docs/adr/0007-reasoning-persisted-but-not-replayed.md）。
--   正因为要落库，记忆窗口与摘要的取数必须**显式剔除**该列：否则每一轮运行都会把
--   最后一屏消息的思考读出来再丢掉（见 RuntimeMessageServiceImpl 的窗口查询）。
--
-- metadata 是**保留列**：本轮只建列，没有任何写入方与读取方（语义待定）。
--   写在这里是为了让「以后要加」不必再改一次表结构；看到它 NULL 别当成 bug。
--   用 text 而非 jsonb：本仓库唯一一次 jsonb 用法已在 V28 降级为 text
--   （从未按 jsonb 路径查询、归一化破坏回显保真、实体 String 绑定与 jsonb 写入冲突）。
--
-- 运行事件表（v5ai_run_event）**仍然不落思考**：事件表会随思考长度膨胀，那是原
--   「思考不落库」决定的真实理由；本迁移只换落点，不改该口径。
-- ============================================================

ALTER TABLE v5ai_message ADD COLUMN IF NOT EXISTS reasoning TEXT;
ALTER TABLE v5ai_message ADD COLUMN IF NOT EXISTS metadata TEXT;

COMMENT ON COLUMN v5ai_message.reasoning IS '模型的思考过程（仅助手消息有值；NULL=没有思考或 V42 之前的历史数据）；供门户回看，不回放给模型';
COMMENT ON COLUMN v5ai_message.metadata IS '消息扩展元数据（JSON 文本）：V42 仅建列、语义待定，当前无写入方与读取方';
