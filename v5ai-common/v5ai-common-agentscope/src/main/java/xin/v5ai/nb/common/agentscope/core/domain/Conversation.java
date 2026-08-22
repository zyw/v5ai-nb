package xin.v5ai.nb.common.agentscope.core.domain;

/**
 * 会话实体（不可变 record），标识一段对话归属于哪个应用。
 *
 * @param conversationId 会话 ID：唯一标识一段多轮对话
 * @param agentKey         应用标识：该会话所属的应用
 */
public record Conversation(String conversationId, String agentKey) {
}
