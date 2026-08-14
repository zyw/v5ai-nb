package xin.v5ai.nb.infrastructure.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity>, KnowledgeDocumentGateway {
    @Override
    default void insertDocument(KnowledgeDocumentEntity entity) {
        insert(entity);
    }

    @Override
    default void update(KnowledgeDocumentEntity entity) {
        updateById(entity);
    }

    @Override
    default void updateContent(Long documentId, byte[] content) {
        var entity = new KnowledgeDocumentEntity();
        entity.setContent(content);
        update(entity, new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getId, documentId));
    }

    @Override
    default KnowledgeDocumentEntity selectKnowledgeDocumentById(Long id) {
        return selectById(id);
    }

    @Override
    default List<KnowledgeDocumentEntity> selectByKnowledgeBaseId(Long knowledgeBaseId) {
        return selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeDocumentEntity::getId));
    }

    @Override
    default void deleteById(Long id) {
        deleteById(id);
    }
}
