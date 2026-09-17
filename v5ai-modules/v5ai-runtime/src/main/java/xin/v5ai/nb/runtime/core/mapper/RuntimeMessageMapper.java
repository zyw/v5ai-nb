//package xin.v5ai.nb.runtime.core.mapper;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.core.mapper.BaseMapper;
//import org.apache.ibatis.annotations.Mapper;
//import xin.v5ai.nb.runtime.core.domain.RuntimeMessageEntity;
//import xin.v5ai.nb.runtime.core.gateway.RuntimeMessageGateway;
//
//import java.util.List;
//
//@Mapper
//public interface RuntimeMessageMapper extends BaseMapper<RuntimeMessageEntity>, RuntimeMessageGateway {
//    @Override
//    default void insertMessage(RuntimeMessageEntity entity) {
//        insert(entity);
//    }
//
//    @Override
//    default List<RuntimeMessageEntity> selectMessagesByConversationId(String conversationId) {
//        return selectList(new LambdaQueryWrapper<RuntimeMessageEntity>()
//                .eq(RuntimeMessageEntity::getConversationId, conversationId)
//                .orderByAsc(RuntimeMessageEntity::getId));
//    }
//}
