-- V46：Agent 次要模型 + 门户 RAG 引用展示开关
--
-- 需求一：Agent 次要模型（供「会话标题改写」与「会话摘要压缩」两处平台内部调用使用）
--   单列引用，与 V44 之后「模型绑定为 v5ai_agent.model_id 单列」的组织方式一致。
--   可空；为空时回退为该 Agent 绑定的对话模型（两级回退，中间不再有全局配置项——
--   v5ai.chat.conversation-summary.model-id 已随本次下线，见 docs/次要模型与引用展示开关实现方案.md §3.1）。
--   不建外键：与近期新增的模型引用列（embedding_model_id / rerank_model_id /
--   v5ai_conversation_summary.model_id）一致；引用拦截由应用层 V5aiModelServiceImpl#verifyNotReferenced
--   负责，能给出「被 N 个 Agent 使用，无法禁用」的友好文案，而裸外键约束只会抛原始约束错误。

ALTER TABLE v5ai_agent ADD COLUMN IF NOT EXISTS secondary_model_id BIGINT;

COMMENT ON COLUMN v5ai_agent.secondary_model_id IS
    '次要模型 id（v5ai_model.id，须为 CHAT 类型且已启用）：供会话标题改写与会话摘要压缩调用；为空则回退绑定的对话模型';

-- 需求二：门户聊天窗口是否展示 RAG 引用
--   默认 true = 存量行为不变。
--   纯展示开关：不改变检索本身、不改变 SSE RETRIEVAL 事件推送、不改变引用落库
--   （引用是「这条回答当时依据了什么」的快照，知识库可改切片/删文档/重建索引，事后无法重建，
--   故开关必须可逆——见 docs/adr/0009-citations-persisted-on-assistant-message.md）。
--   作用面：门户聊天窗口 + 管理端「预览与调试」（调试页可当场看到显隐效果）；
--   知识库「知识问答」不属于任何 Agent，不受本项影响。

ALTER TABLE v5ai_agent ADD COLUMN IF NOT EXISTS show_citations BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN v5ai_agent.show_citations IS
    '是否在聊天窗口展示 RAG 引用折叠块；默认 true；仅影响渲染，引用照常检索与落库（见 docs/adr/0009）';
