package xin.v5ai.nb.runtime.core;

/**
 * 会话摘要生成端口：回答落库之后，把历史窗口之外的旧内容压缩成一段摘要。
 *
 * <p>它是**可选能力**：实现必须异步、失败静默——摘要只是「让模型别忘事」，不能反过来影响
 * 对话主链路（窗口内的历史照常回放，旧摘要仍在库里）。实现见 {@link ConversationSummaryWriter}；
 * 测试可以传 lambda 或 {@code null}。</p>
 */
@FunctionalInterface
public interface ConversationSummaryGenerator {

    /**
     * 必要时生成/更新该会话的摘要（实现内部判定阈值与水位）。
     *
     * @param conversationId 会话 ID
     * @param agentKey       Agent 标识（默认用它绑定的 CHAT 模型生成摘要）
     */
    void summarizeIfNeeded(String conversationId, String agentKey);
}
