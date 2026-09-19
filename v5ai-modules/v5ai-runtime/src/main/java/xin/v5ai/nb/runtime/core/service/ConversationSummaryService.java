package xin.v5ai.nb.runtime.core.service;

import xin.v5ai.nb.runtime.core.domain.ConversationSummaryDTO;

import java.util.Optional;

/**
 * 会话摘要端口：同会话历史窗口之外的内容压缩成一段文本，继续参与上下文。
 *
 * <p>一会话一行、滚动覆盖；写入口只在「覆盖水位更高」时生效，因此并发或乱序生成都不会把摘要改旧。</p>
 */
public interface ConversationSummaryService {

    /**
     * 读取会话摘要。
     *
     * @param conversationId 会话 ID
     * @return 摘要；没有（或正文为空）时为空
     */
    Optional<ConversationSummaryDTO> find(String conversationId);

    /**
     * 滚动覆盖写入：仅当新水位更高时生效。
     *
     * @param conversationId        会话 ID
     * @param summary               摘要正文
     * @param coveredUntilMessageId 新的覆盖水位
     * @param coveredMessages       已覆盖的消息条数
     * @param modelId               生成摘要的模型 id（可空）
     * @return 是否写入（false = 已有更新的摘要，本次结果被丢弃）
     */
    boolean upsertIfNewer(String conversationId, String summary, long coveredUntilMessageId,
                          int coveredMessages, Long modelId);

    /**
     * 摘要覆盖了「重新生成」锚点之后的内容时整条作废。
     *
     * <p>被作废的问答不该继续留在模型记忆里；失效后下次运行会按当前历史重新生成。</p>
     *
     * @param conversationId 会话 ID
     * @param messageId      锚点提问 id
     * @return 是否删除了摘要
     */
    boolean invalidateIfCoveringAfter(String conversationId, Long messageId);

    /**
     * Agent 级联删除：清理该 Agent 名下所有会话的摘要（会话本身由调用方删除）。
     *
     * @param agentKey Agent 标识
     * @return 删除行数
     */
    int deleteByAgentKey(String agentKey);
}
