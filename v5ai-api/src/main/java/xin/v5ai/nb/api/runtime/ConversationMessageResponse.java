package xin.v5ai.nb.api.runtime;

import xin.v5ai.nb.runtime.Message;
import xin.v5ai.nb.runtime.MessageRole;

public record ConversationMessageResponse(MessageRole role, String content) {
    static ConversationMessageResponse from(Message message) {
        return new ConversationMessageResponse(message.role(), message.content());
    }
}
