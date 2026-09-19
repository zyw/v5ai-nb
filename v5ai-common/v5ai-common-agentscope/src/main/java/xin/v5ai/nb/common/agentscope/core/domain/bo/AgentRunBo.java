package xin.v5ai.nb.common.agentscope.core.domain.bo;

import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;

import java.util.List;

/**
 * Agent 运行请求的参数载体（不可变 record）。
 *
 * 通过构造器链式设置各字段，字段越往后越"高级"：
 * - 最简单的调用只需要 agentKey + conversationId + query；
 * - 可选追加 RAG 上下文（知识库检索结果）、历史消息与平台运行 ID（runId）；
 * - {@code webSearch} 是本次请求对「联网搜索」的显式意图，只用于**收窄**：
 *   Agent 未开启联网时无论如何都不会联网；
 * - {@code attachments} 是本次提问随消息提交的图片（已上传为资源，只带引用）；
 * - {@code disabledMcpServerIds} / {@code disabledSkillIds} 同样是**只减不增**的收窄项：
 *   它们从 Agent 的绑定列表里剔除若干项，永远不会让 Agent 未绑定的能力出现。
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
        Boolean webSearch,
        /** 本次提问携带的附件（图片）；无附件为空列表 */
        List<AttachmentRef> attachments,
        /** 本次运行要收窄掉的 MCP Server（只能减少绑定，不能增加） */
        List<Long> disabledMcpServerIds,
        /** 本次运行要收窄掉的 Skill（只能减少绑定，不能增加） */
        List<Long> disabledSkillIds,
        /** 发起本次运行的 API Key（门户侧的租户边界）：由服务端从鉴权结果注入，**绝不接受请求体传入** */
        Long apiKeyId,
        /** 发起本次运行的用户：仅供展示/审计，不作为归属依据 */
        Long userId,
        /**
         * 本次运行是否复用已存在的提问行（「重新生成」）：为 true 时不再新插用户消息——
         * 那一轮的提问保持有效、只是回答被替换，因此历史里不会出现两条相同的提问
         */
        boolean reuseUserMessage,
        /**
         * 会话摘要：历史窗口之外的旧内容被压缩成的一段文本；由服务端按会话装配，
         * 与 RAG 上下文并列拼进系统提示（可为 null）。**同样绝不接受请求体传入**
         */
        String summaryContext
) {
    /**
     * 归一化：列表字段永不为 null，避免每个消费方各写一次判空。
     */
    public AgentRunBo {
        history = history == null ? List.of() : List.copyOf(history);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        disabledMcpServerIds = disabledMcpServerIds == null ? List.of() : List.copyOf(disabledMcpServerIds);
        disabledSkillIds = disabledSkillIds == null ? List.of() : List.copyOf(disabledSkillIds);
    }

    /**
     * 精简构造器：仅提供核心三要素，
     * ragContext 置为 null，history 置为空列表，runId/webSearch 置为 null，附件与收窄项为空。
     */
    public AgentRunBo(String agentKey, String conversationId, String query) {
        this(agentKey, conversationId, query, null, List.of(), null, null, List.of(), List.of(), List.of(),
                null, null, false, null);
    }

    /**
     * 兼容构造器：不指定联网意图（沿用 Agent 配置），也不带附件、收窄项与调用方身份。
     */
    public AgentRunBo(String agentKey, String conversationId, String query, String ragContext,
                      List<SessionMessage> history, String runId) {
        this(agentKey, conversationId, query, ragContext, history, runId, null, List.of(), List.of(), List.of(),
                null, null, false, null);
    }

    /**
     * 兼容构造器：不带附件、收窄项与调用方身份（webSearch 之前的全部字段）。
     */
    public AgentRunBo(String agentKey, String conversationId, String query, String ragContext,
                      List<SessionMessage> history, String runId, Boolean webSearch) {
        this(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                List.of(), List.of(), List.of(), null, null, false, null);
    }

    /**
     * 返回一个换用指定会话 ID 的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withConversationId(String conversationId) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个带 RAG 上下文的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withRagContext(String context) {
        return new AgentRunBo(agentKey, conversationId, query, context, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个带历史消息的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withHistory(List<SessionMessage> history) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个带平台运行 ID 的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withRunId(String runId) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个带联网意图的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withWebSearch(Boolean webSearch) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个带附件的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withAttachments(List<AttachmentRef> attachments) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个收窄 MCP Server 的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withDisabledMcpServerIds(List<Long> disabledMcpServerIds) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个收窄 Skill 的新请求（不可变，不影响原对象）。
     */
    public AgentRunBo withDisabledSkillIds(List<Long> disabledSkillIds) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个带调用方身份的新请求（不可变，不影响原对象）。
     *
     * <p>只在服务端装配入参时使用：身份来自已校验的鉴权结果，不来自调用方提交的内容。</p>
     */
    public AgentRunBo withCaller(Long apiKeyId, Long userId) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个「复用既有提问行」的新请求（不可变，不影响原对象）。
     *
     * <p>只由「重新生成」使用：那一轮的提问保持有效，本轮不再新插用户消息。</p>
     */
    public AgentRunBo withReuseUserMessage(boolean reuseUserMessage) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
    }

    /**
     * 返回一个带会话摘要的新请求（不可变，不影响原对象）。
     *
     * <p>只在服务端装配上下文时使用：摘要派生自本会话的历史，不来自调用方提交的内容。</p>
     */
    public AgentRunBo withSummaryContext(String summaryContext) {
        return new AgentRunBo(agentKey, conversationId, query, ragContext, history, runId, webSearch,
                attachments, disabledMcpServerIds, disabledSkillIds, apiKeyId, userId, reuseUserMessage,
                summaryContext);
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

    /**
     * 本次是否携带了附件（图片）。
     */
    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }
}
