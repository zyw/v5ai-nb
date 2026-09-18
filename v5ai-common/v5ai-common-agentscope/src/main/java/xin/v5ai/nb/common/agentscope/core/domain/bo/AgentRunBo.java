package xin.v5ai.nb.common.agentscope.core.domain.bo;

import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;

import java.util.List;

/**
 * Agent 运行请求的参数载体（不可变 record）。
 *
 * 通过构造器链式设置各字段，字段越往后越"高级"：
 * - 最简单的调用只需要 agentKey + conversationId + query；
 * - 可选追加 RAG 上下文（知识库检索结果）、历史消息与平台运行 ID（runId）；
 * - {@code webSearch} 是本次请求对「联网搜索」的显式意图，只用于**收窄**：
 *   Agent 未开启联网时无论如何都不会联网。
 */
public record AgentRunBo(
        /** Agent 标识（agentKey）：调用哪个 Agent */
        String agentKey,
        /** 会话 ID：区分同一 Agent 下的不同对话 */
        String conversationId,
        /** 用户本次提问的内容 */
        String query,
        /** RAG 上下文：检索到的知识库片段（可为 null） */
        String ragContext,
        /** 历史消息：多轮对话的上下文（可为空列表） */
        List<SessionMessage> history,
        /** 平台运行 ID：由运行时生成，用于 MCP Tool 审计等关联（可为 null） */
        String runId,
        /** 本次是否联网：null=按 Agent 配置，false=显式关闭，true=请求开启（仍需 Agent 允许） */
        Boolean webSearch
) {
    /**
     * 精简构造器：仅提供核心三要素，
     * ragContext 置为 null，history 置为空列表，runId 与 webSearch 置为 null。
     */
    public AgentRunBo(String agentKey, String conversationId, String query) {
        this(agentKey, conversationId, query, null, List.of(), null, null);
    }

    /**
     * 兼容构造器：不指定联网意图（沿用 Agent 配置）。
     */
    public AgentRunBo(String agentKey, String conversationId, String query, String ragContext,
                      List<SessionMessage> history, String runId) {
        this(agentKey, conversationId, query, ragContext, history, runId, null);
    }

    /**
     * 返回一个带 RAG 上下文的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withRagContext(String context) {
        return new AgentRunBo(agentKey, conversationId, query, context, history, runId, webSearch);
    }

    /**
     * 返回一个带历史消息的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withHistory(List<SessionMessage> history) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch);
    }

    /**
     * 返回一个带平台运行 ID 的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withRunId(String runId) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch);
    }

    /**
     * 返回一个带联网意图的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withWebSearch(Boolean webSearch) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch);
    }

    /**
     * 最终是否启用联网工具：以 Agent 配置为准，请求只能显式关闭、不能强行开启。
     *
     * @param agentEnabled Agent 配置里的 webSearchEnabled
     * @return 是否需要注册联网搜索工具
     */
    public boolean resolveWebSearch(boolean agentEnabled) {
        return agentEnabled && !Boolean.FALSE.equals(webSearch);
    }
}
