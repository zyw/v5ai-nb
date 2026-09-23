package xin.v5ai.nb.runtime.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;

import java.util.List;
import java.util.Optional;

/**
 * 消息仓储端口：负责会话消息的持久化。
 *
 * <p>「作废」（superseded）是消息的软状态：被「重新生成」替换掉的消息保留数据但不再参与
 * 展示与记忆回放。本端口的所有读取都不返回作废消息。</p>
 */
public interface MessageService {
    /**
     * 保存一条消息（用户提问或助手回答）。
     *
     * @param message 要保存的消息
     * @return 新消息主键；保存失败时返回 {@code null}
     */
    Long save(SessionMessage message);

    /**
     * 查询某个会话的全部历史消息（通常按时间正序返回），**不含已作废的消息**。
     *
     * @param conversationId 会话 ID
     * @return 该会话的消息列表
     */
    List<SessionMessage> findByConversationId(String conversationId);

    /**
     * 查询某个会话**最近**的若干条历史消息（时间正序返回），**不含已作废的消息**。
     *
     * <p>供运行时按历史窗口加载：长会话不该每次都把整张消息表读出来再在内存里裁。</p>
     *
     * @param conversationId 会话 ID
     * @param limit          最多取多少条；{@code <= 0} 表示不限（等价于
     *                       {@link #findByConversationId(String)}）
     * @return 该会话最近的消息（时间正序）
     */
    List<SessionMessage> findRecentByConversationId(String conversationId, int limit);

    /**
     * 取会话中间的一段未作废消息：{@code afterMessageId}（不含）到 {@code beforeMessageId}（不含）。
     *
     * <p>供会话摘要按水位增量压缩：取的是该区间里**时间上最近的** {@code limit} 条（时间正序返回），
     * 这样摘要始终紧贴历史窗口的起点，模型读到的上下文是连续的。</p>
     *
     * @param conversationId    会话 ID
     * @param afterMessageId    下界（不含）；{@code null} 表示从头开始
     * @param beforeMessageId   上界（不含），通常是历史窗口内最早那条消息的 id
     * @param limit             最多返回多少条；{@code <= 0} 表示不限
     * @return 区间内的消息（时间正序）
     */
    List<SessionMessage> findActiveBetween(String conversationId, Long afterMessageId, Long beforeMessageId,
                                           int limit);

    /**
     * 按主键取一条**未作废**的消息（含附件与用量），供「重新生成」校验锚点提问。
     *
     * @param messageId 消息主键
     * @return 消息；不存在或已作废时为空
     */
    Optional<SessionMessage> findActiveMessage(Long messageId);

    /**
     * 作废该会话中位于指定消息**之后**的全部消息（被替换的回答，以及更晚的整轮问答）。
     *
     * <p>锚点消息本身保持有效：重新生成复用它、不新插提问行，历史里因此不会出现两条相同提问。
     * 幂等——已作废的消息不会被重复标记。</p>
     *
     * @param conversationId 会话 ID
     * @param messageId      锚点消息主键（该轮的用户提问）
     * @return 本次新作废的消息条数
     */
    int supersedeAfter(String conversationId, Long messageId);
}
