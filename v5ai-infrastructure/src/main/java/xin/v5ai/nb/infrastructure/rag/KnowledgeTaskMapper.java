package xin.v5ai.nb.infrastructure.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeTaskMapper extends BaseMapper<KnowledgeTaskEntity>, KnowledgeTaskGateway {
    @Override
    default void insertTask(KnowledgeTaskEntity entity) {
        insert(entity);
    }

    @Override
    default void update(KnowledgeTaskEntity entity) {
        updateById(entity);
    }

    @Override
    default KnowledgeTaskEntity selectTaskById(Long id) {
        return selectById(id);
    }

    @Override
    default List<KnowledgeTaskEntity> selectByDocumentId(Long documentId) {
        return selectList(new LambdaQueryWrapper<KnowledgeTaskEntity>()
                .eq(KnowledgeTaskEntity::getDocumentId, documentId));
    }

    @Override
    default List<KnowledgeTaskEntity> selectByKnowledgeBaseId(Long knowledgeBaseId) {
        return selectList(new LambdaQueryWrapper<KnowledgeTaskEntity>()
                .eq(KnowledgeTaskEntity::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeTaskEntity::getId));
    }

    @Override
    default void deleteByDocumentId(Long documentId) {
        delete(new LambdaQueryWrapper<KnowledgeTaskEntity>()
                .eq(KnowledgeTaskEntity::getDocumentId, documentId));
    }

    @Override
    @Select("""
            SELECT * FROM v5ai_knowledge_task
            WHERE status = 'PENDING'
               OR (status = 'FAILED' AND attempt_count < max_attempts)
            ORDER BY id
            LIMIT #{limit}
            """)
    List<KnowledgeTaskEntity> selectProcessable(@Param("limit") int limit);
}
