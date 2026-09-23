package xin.v5ai.nb.common.agentscope.core.domain;

import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;

import java.util.List;

/**
 * 会话消息实体（不可变 record）。
 *
 * @param conversationId 所属会话 ID
 * @param agentKey         所属 Agent 标识
 * @param role           消息角色（用户/助手）
 * @param content        消息内容（正文）
 * @param reasoning      模型的思考过程（仅助手消息有值；用户消息与没有思考的历史消息为 null）。
 *                       它**只供回看**：不进模型上下文、不占历史窗口预算、也不进会话摘要
 * @param citations      本条回答当时引用过的切片（仅助手消息有值；空列表 = 没有引用）。
 *                       与 {@code reasoning} 同口径：**只供回看**，不回放给模型
 * @param attachments    消息携带的附件（图片）；助手消息恒为空列表
 * @param usage          该回答的用量与用时；用户消息、或没有用量记录的历史消息为 null
 * @param messageId      持久化主键；由调用方临时构造（未落库）的消息为 null。
 *                       「重新生成」以该轮的**提问**消息 id 为锚点，因此历史接口必须把它交给前端
 */
public record SessionMessage(
        String conversationId,
        String agentKey,
        MessageRole role,
        String content,
        String reasoning,
        List<RagHit> citations,
        List<AttachmentRef> attachments,
        RunUsage usage,
        Long messageId) {

    /**
     * 归一化：附件与引用列表永不为 null（无附件/无引用即空列表），避免每个消费方各写一次判空；
     * 思考归一化为 null（空串不表示「思考过但没内容」，那是「没有思考」）。
     */
    public SessionMessage {
        citations = citations == null ? List.of() : List.copyOf(citations);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        reasoning = (reasoning == null || reasoning.isBlank()) ? null : reasoning;
    }

    /**
     * 兼容构造器：不带思考、引用、附件、用量与主键的纯文本消息。
     */
    public SessionMessage(String conversationId, String agentKey, MessageRole role, String content) {
        this(conversationId, agentKey, role, content, null, List.of(), List.of(), null, null);
    }

    /**
     * 兼容构造器：只带附件的消息（用户消息）。
     */
    public SessionMessage(String conversationId, String agentKey, MessageRole role, String content,
                          List<AttachmentRef> attachments) {
        this(conversationId, agentKey, role, content, null, List.of(), attachments, null, null);
    }

    /**
     * 兼容构造器：不带主键的消息。
     */
    public SessionMessage(String conversationId, String agentKey, MessageRole role, String content,
                          List<AttachmentRef> attachments, RunUsage usage) {
        this(conversationId, agentKey, role, content, null, List.of(), attachments, usage, null);
    }

    /**
     * 返回一条带附件的新消息（不可变，不影响原对象）。
     */
    public SessionMessage withAttachments(List<AttachmentRef> attachments) {
        return new SessionMessage(conversationId, agentKey, role, content, reasoning, citations, attachments,
                usage, messageId);
    }

    /**
     * 返回一条带引用的新消息（不可变，不影响原对象）。空列表归一化为空列表，语义是「没有引用」。
     */
    public SessionMessage withCitations(List<RagHit> citations) {
        return new SessionMessage(conversationId, agentKey, role, content, reasoning, citations, attachments,
                usage, messageId);
    }

    /**
     * 返回一条带用量的新消息（不可变，不影响原对象）。
     */
    public SessionMessage withUsage(RunUsage usage) {
        return new SessionMessage(conversationId, agentKey, role, content, reasoning, citations, attachments,
                usage, messageId);
    }

    /**
     * 返回一条带持久化主键的新消息（不可变，不影响原对象）。
     */
    public SessionMessage withMessageId(Long messageId) {
        return new SessionMessage(conversationId, agentKey, role, content, reasoning, citations, attachments,
                usage, messageId);
    }

    /**
     * 返回一条带思考过程的新消息（不可变，不影响原对象）。
     *
     * <p>空白思考会被归一化为 {@code null}（见紧凑构造器）。</p>
     */
    public SessionMessage withReasoning(String reasoning) {
        return new SessionMessage(conversationId, agentKey, role, content, reasoning, citations, attachments,
                usage, messageId);
    }
}
