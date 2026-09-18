package xin.v5ai.nb.common.agentscope.core.domain;

import xin.v5ai.nb.common.agentscope.enums.MessageRole;

import java.util.List;

/**
 * 会话消息实体（不可变 record）。
 *
 * @param conversationId 所属会话 ID
 * @param agentKey         所属 Agent 标识
 * @param role           消息角色（用户/助手）
 * @param content        消息内容
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
        List<AttachmentRef> attachments,
        RunUsage usage,
        Long messageId) {

    /**
     * 归一化：附件列表永不为 null（无附件即空列表），避免每个消费方各写一次判空。
     */
    public SessionMessage {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /**
     * 兼容构造器：不带附件、用量与主键的纯文本消息。
     */
    public SessionMessage(String conversationId, String agentKey, MessageRole role, String content) {
        this(conversationId, agentKey, role, content, List.of(), null, null);
    }

    /**
     * 兼容构造器：只带附件的消息（用户消息）。
     */
    public SessionMessage(String conversationId, String agentKey, MessageRole role, String content,
                          List<AttachmentRef> attachments) {
        this(conversationId, agentKey, role, content, attachments, null, null);
    }

    /**
     * 兼容构造器：不带主键的消息。
     */
    public SessionMessage(String conversationId, String agentKey, MessageRole role, String content,
                          List<AttachmentRef> attachments, RunUsage usage) {
        this(conversationId, agentKey, role, content, attachments, usage, null);
    }

    /**
     * 返回一条带附件的新消息（不可变，不影响原对象）。
     */
    public SessionMessage withAttachments(List<AttachmentRef> attachments) {
        return new SessionMessage(conversationId, agentKey, role, content, attachments, usage, messageId);
    }

    /**
     * 返回一条带用量的新消息（不可变，不影响原对象）。
     */
    public SessionMessage withUsage(RunUsage usage) {
        return new SessionMessage(conversationId, agentKey, role, content, attachments, usage, messageId);
    }

    /**
     * 返回一条带持久化主键的新消息（不可变，不影响原对象）。
     */
    public SessionMessage withMessageId(Long messageId) {
        return new SessionMessage(conversationId, agentKey, role, content, attachments, usage, messageId);
    }
}
