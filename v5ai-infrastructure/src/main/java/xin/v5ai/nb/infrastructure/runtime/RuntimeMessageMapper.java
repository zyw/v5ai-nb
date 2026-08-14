package xin.v5ai.nb.infrastructure.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RuntimeMessageMapper extends BaseMapper<RuntimeMessageEntity>, RuntimeMessageGateway {
    @Override
    default void insertMessage(RuntimeMessageEntity entity) {
        insert(entity);
    }

    @Override
    default List<RuntimeMessageEntity> selectMessagesByConversationId(String conversationId) {
        return selectList(new LambdaQueryWrapper<RuntimeMessageEntity>()
                .eq(RuntimeMessageEntity::getConversationId, conversationId)
                .orderByAsc(RuntimeMessageEntity::getId));
    }
}
