package xin.v5ai.nb.infrastructure.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ApplicationKnowledgeBindingMapper extends BaseMapper<ApplicationKnowledgeBindingEntity>,
        ApplicationKnowledgeBindingGateway {
    @Override
    default void insertBinding(ApplicationKnowledgeBindingEntity entity) {
        insert(entity);
    }

    @Override
    default void deleteByAppKey(String appKey) {
        delete(new LambdaQueryWrapper<ApplicationKnowledgeBindingEntity>()
                .eq(ApplicationKnowledgeBindingEntity::getAppKey, appKey));
    }

    @Override
    default List<ApplicationKnowledgeBindingEntity> selectByAppKey(String appKey) {
        return selectList(new LambdaQueryWrapper<ApplicationKnowledgeBindingEntity>()
                .eq(ApplicationKnowledgeBindingEntity::getAppKey, appKey));
    }
}
