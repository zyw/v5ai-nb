package xin.v5ai.nb.runtime;

public record Message(String conversationId, String appKey, MessageRole role, String content) {
}
