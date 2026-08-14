package xin.v5ai.nb.runtime;

import java.util.List;

/**
 * Agent 运行请求的参数载体（不可变 record）。
 *
 * 通过构造器链式设置各字段，字段越往后越"高级"：
 * - 最简单的调用只需要 appKey + conversationId + query；
 * - 可选追加 RAG 上下文（知识库检索结果）与历史消息。
 */
public record AgentRunRequest(
        /** 应用标识：调用哪个 Agent 应用 */
        String appKey,
        /** 会话 ID：区分同一应用下的不同对话 */
        String conversationId,
        /** 用户本次提问的内容 */
        String query,
        /** RAG 上下文：检索到的知识库片段（可为 null） */
        String ragContext,
        /** 历史消息：多轮对话的上下文（可为空列表） */
        List<Message> history
) {
    /**
     * 精简构造器：仅提供核心三要素，
     * ragContext 置为 null，history 置为空列表。
     */
    public AgentRunRequest(String appKey, String conversationId, String query) {
        this(appKey, conversationId, query, null, List.of());
    }

    /**
     * 返回一个带 RAG 上下文的新请求（不可变，不影响原对象）。
     */
    public AgentRunRequest withRagContext(String context) {
        return new AgentRunRequest(appKey, conversationId, query, context, history);
    }

    /**
     * 返回一个带历史消息的新请求（不可变，不影响原对象）。
     */
    public AgentRunRequest withHistory(List<Message> history) {
        return new AgentRunRequest(appKey, conversationId, query, ragContext, history);
    }
}
