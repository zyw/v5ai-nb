package xin.v5ai.nb.runtime.core;

import xin.v5ai.nb.runtime.core.utils.ConversationNaming;

/**
 * 会话标题改写端口：首轮结束后用模型把兜底名换成一个短标题。
 *
 * <p>它是**可选能力**：实现里发生的一切失败都必须自己吞掉——兜底名（{@link ConversationNaming#fromQuery}）
 * 已经在会话创建时落库，标题只是锦上添花，不能反过来影响对话主链路。
 * 真正的实现见 {@link ConversationTitleWriter}；测试可以传 lambda 或 {@code null}。</p>
 */
@FunctionalInterface
public interface ConversationTitleGenerator {

    /**
     * 仅在「名称仍由系统生成」时改写成模型标题（用户改过的名字不会被覆盖）。
     *
     * @param conversationId 会话 ID
     * @param agentKey       Agent 标识（用它绑定的 CHAT 模型生成标题）
     * @param question       该会话的首条提问
     */
    void rewriteIfAutoNamed(String conversationId, String agentKey, String question);
}
