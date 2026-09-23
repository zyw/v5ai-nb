package xin.v5ai.nb.runtime.core.domain;

import java.time.OffsetDateTime;

/**
 * 会话历史摘要（读取侧载体）。
 *
 * @param conversationId        会话 ID
 * @param summary               摘要正文（注入系统提示的那段文本）
 * @param coveredUntilMessageId 覆盖水位：id ≤ 该值的消息都已并入摘要
 * @param coveredMessages       已覆盖的消息条数（观测用）
 * @param modelId               生成摘要的模型 id（可空）
 * @param updatedAt             最近一次生成时间（防抖判定用）
 */
public record ConversationSummaryDTO(String conversationId, String summary, long coveredUntilMessageId,
                                     int coveredMessages, Long modelId, OffsetDateTime updatedAt) {
}
