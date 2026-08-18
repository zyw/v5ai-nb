package xin.v5ai.nb.infrastructure.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseEntity>, KnowledgeBaseGateway {
    @Override
    default void insertKnowledgeBase(KnowledgeBaseEntity entity) {
        insert(entity);
    }

    @Override
    default KnowledgeBaseEntity selectKnowledgeBaseById(Long id) {
        return selectById(id);
    }

    @Override
    default List<KnowledgeBaseEntity> selectAll() {
        return selectList(null);
    }

    @Override
    default List<KnowledgeBaseEntity> selectOptions() {
        return selectList(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .select(KnowledgeBaseEntity::getId, KnowledgeBaseEntity::getName));
    }

    @Override
    default void disable(Long id) {
        var entity = new KnowledgeBaseEntity();
        entity.setStatus("DISABLED");
        update(entity, new LambdaUpdateWrapper<KnowledgeBaseEntity>().eq(KnowledgeBaseEntity::getId, id));
    }
}
