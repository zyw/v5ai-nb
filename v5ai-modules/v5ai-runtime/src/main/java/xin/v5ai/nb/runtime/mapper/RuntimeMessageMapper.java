package xin.v5ai.nb.runtime.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.runtime.domain.RuntimeMessage;
import xin.v5ai.nb.runtime.domain.vo.RuntimeConversationPreviewVo;
import xin.v5ai.nb.runtime.domain.vo.RuntimeMessageVo;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RuntimeMessageMapper extends BaseMapperPlus<RuntimeMessage, RuntimeMessageVo> {

    /**
     * 批量取每个会话的首条用户消息（会话列表未命名时的展示兜底）。
     *
     * <p>用 PostgreSQL 的 {@code DISTINCT ON} 一次查完，避免按会话逐条查询（N+1）；
     * 排序键与消息回放一致（{@code created_at, id}），保证「首条」与用户看到的顺序相同。</p>
     *
     * @param conversationIds 会话 ID 集合（调用方保证非空）
     * @return 每个会话的首条用户消息
     */
    @Select("""
            <script>
            SELECT DISTINCT ON (conversation_id)
                   conversation_id AS "conversationId",
                   content         AS "content"
            FROM v5ai_message
            WHERE role = 'USER'
              AND conversation_id IN
              <foreach collection="conversationIds" item="conversationId" open="(" separator="," close=")">
                #{conversationId}
              </foreach>
            ORDER BY conversation_id, created_at, id
            </script>
            """)
    List<RuntimeConversationPreviewVo> selectFirstUserMessages(
            @Param("conversationIds") Collection<String> conversationIds);
}
