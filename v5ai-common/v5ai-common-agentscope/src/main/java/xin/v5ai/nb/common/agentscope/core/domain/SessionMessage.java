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
 */
public record SessionMessage(
        String conversationId,
        String agentKey,
        MessageRole role,
        String content,
        List<AttachmentRef> attachments) {

    /**
     * 归一化：附件列表永不为 null（无附件即空列表），避免每个消费方各写一次判空。
     */
    public SessionMessage {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /**
     * 兼容构造器：不带附件的纯文本消息。
     */
    public SessionMessage(String conversationId, String agentKey, MessageRole role, String content) {
        this(conversationId, agentKey, role, content, List.of());
    }

    /**
     * 返回一条带附件的新消息（不可变，不影响原对象）。
     */
    public SessionMessage withAttachments(List<AttachmentRef> attachments) {
        return new SessionMessage(conversationId, agentKey, role, content, attachments);
    }
}
