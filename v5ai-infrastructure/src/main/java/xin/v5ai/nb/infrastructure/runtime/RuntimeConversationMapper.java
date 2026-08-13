package xin.v5ai.nb.infrastructure.runtime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuntimeConversationMapper extends BaseMapper<RuntimeConversationEntity>, RuntimeConversationGateway {
    @Override
    default void upsertConversation(RuntimeConversationEntity entity) {
        insertOrUpdate(entity);
    }
}
