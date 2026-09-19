package xin.v5ai.nb.runtime.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.runtime.domain.ConversationSummary;
import xin.v5ai.nb.runtime.domain.vo.ConversationSummaryVo;

@Mapper
public interface ConversationSummaryMapper extends BaseMapperPlus<ConversationSummary, ConversationSummaryVo> {

    /**
     * 滚动覆盖：只在覆盖水位更高时写入。
     *
     * <p>判定交给数据库（{@code ON CONFLICT ... WHERE}）而不是「先查后写」：摘要生成是异步的，
     * 同一会话可能同时有两次生成在跑，先查后写会让慢的那次把新摘要改旧。</p>
     *
     * @param conversationId          会话 ID
     * @param summary                 摘要正文
     * @param coveredUntilMessageId   新的覆盖水位
     * @param coveredMessages         已覆盖的消息条数（观测用）
     * @param modelId                 生成摘要的模型 id（可空）
     * @return 影响行数；水位没有前进时为 0
     */
    int upsertIfNewer(@Param("conversationId") String conversationId,
                      @Param("summary") String summary,
                      @Param("coveredUntilMessageId") long coveredUntilMessageId,
                      @Param("coveredMessages") int coveredMessages,
                      @Param("modelId") Long modelId);

    /**
     * 重新生成：摘要覆盖了锚点**之后**的内容时整条作废（避免模型记住已被作废的回答）。
     *
     * @param conversationId 会话 ID
     * @param messageId      重新生成的锚点提问 id
     * @return 删除行数（0 表示摘要没有覆盖到被作废的部分）
     */
    int deleteIfCoveringAfter(@Param("conversationId") String conversationId,
                              @Param("messageId") Long messageId);

    /**
     * Agent 级联删除：把该 Agent 名下所有会话的摘要一并清理。
     *
     * @param agentKey Agent 标识
     * @return 删除行数
     */
    int deleteByAgentKey(@Param("agentKey") String agentKey);
}
