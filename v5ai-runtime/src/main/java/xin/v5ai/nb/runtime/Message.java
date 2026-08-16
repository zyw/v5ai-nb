package xin.v5ai.nb.runtime;

/**
 * 会话消息实体（不可变 record）。
 *
 * @param conversationId 所属会话 ID
 * @param agentKey         所属应用标识
 * @param role           消息角色（用户/助手）
 * @param content        消息内容
 */
public record Message(String conversationId, String agentKey, MessageRole role, String content) {
}
