package xin.v5ai.nb.runtime.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.runtime.core.domain.RuntimeConversationEntity;
import xin.v5ai.nb.runtime.core.gateway.RuntimeConversationGateway;

@Mapper
public interface RuntimeConversationMapper extends BaseMapper<RuntimeConversationEntity>, RuntimeConversationGateway {
    @Override
    default void upsertConversation(RuntimeConversationEntity entity) {
        insertOrUpdate(entity);
    }
}
