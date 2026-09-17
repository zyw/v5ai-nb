//package xin.v5ai.nb.runtime.core.mapper;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.core.mapper.BaseMapper;
//import org.apache.ibatis.annotations.Mapper;
//import xin.v5ai.nb.runtime.core.domain.AgentStateEntity;
//import xin.v5ai.nb.runtime.core.gateway.AgentStateGateway;
//
//@Mapper
//public interface AgentStateMapper extends BaseMapper<AgentStateEntity>, AgentStateGateway {
//    @Override
//    default void upsert(AgentStateEntity entity) {
//        var existing = selectByConversationId(entity.getConversationId());
//        if (existing == null) {
//            insert(entity);
//        } else {
//            entity.setConversationId(existing.getConversationId());
//            updateById(entity);
//        }
//    }
//
//    @Override
//    default AgentStateEntity selectByConversationId(String conversationId) {
//        return selectOne(new LambdaQueryWrapper<AgentStateEntity>()
//                .eq(AgentStateEntity::getConversationId, conversationId));
//    }
//}
