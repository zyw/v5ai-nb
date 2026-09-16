package xin.v5ai.nb.runtime.controller.vo;

import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;

/**
 * 会话消息的 API 响应体（不可变 record）。
 *
 * @param role    消息角色（USER/ASSISTANT）
 * @param content 消息内容
 */
public record ConversationMessageResponse(MessageRole role, String content) {
    /**
     * 将领域消息 {@link SessionMessage} 转为 API 响应体（去掉会话/应用等内部字段）。
     */
    public static ConversationMessageResponse from(SessionMessage message) {
        return new ConversationMessageResponse(message.role(), message.content());
    }
}
