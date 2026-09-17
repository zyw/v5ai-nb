package xin.v5ai.nb.common.agentscope.core.domain.bo;

/**
 * Chat 接口请求体（不可变 record）。
 *
 * @param conversationId 会话 ID（可为空，为空时后端会自动创建新会话）
 * @param query          用户提问内容
 */
public record ChatBo(String conversationId, String query) {
}
