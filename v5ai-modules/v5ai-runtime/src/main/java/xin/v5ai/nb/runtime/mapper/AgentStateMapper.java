package xin.v5ai.nb.runtime.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.runtime.domain.AgentState;
import xin.v5ai.nb.runtime.domain.vo.AgentStateVo;

@Mapper
public interface AgentStateMapper extends BaseMapperPlus<AgentState, AgentStateVo> {

    default int upsert(AgentState entity) {
        var existing = selectByConversationId(entity.getConversationId());
        if (existing == null) {
            return insert(entity);
        } else {
            entity.setConversationId(existing.getConversationId());
            return updateById(entity);
        }
    }

    default AgentState selectByConversationId(String conversationId) {
        return selectOne(new LambdaQueryWrapper<AgentState>()
                .eq(AgentState::getConversationId, conversationId));
    }
}
