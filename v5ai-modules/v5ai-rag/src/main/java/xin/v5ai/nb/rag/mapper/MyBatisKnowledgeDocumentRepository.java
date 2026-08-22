package xin.v5ai.nb.rag.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.KnowledgeDocumentRepository;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;

import java.util.List;

/**
 * {@link KnowledgeDocumentRepository} / {@link KnowledgeDocumentContentStore} 的
 * MyBatis-Plus 实现（Worker 索引链路与检索上下文消费）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
public class MyBatisKnowledgeDocumentRepository implements KnowledgeDocumentRepository, KnowledgeDocumentContentStore {

    private final KnowledgeDocumentMapper documentMapper;

    public MyBatisKnowledgeDocumentRepository(KnowledgeDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        if (document.getId() == null) {
            documentMapper.insert(document);
        } else {
            documentMapper.updateById(document);
        }
        return document;
    }

    @Override
    public KnowledgeDocument findById(Long id) {
        return documentMapper.selectById(id);
    }

    @Override
    public List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId) {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeDocument::getId));
    }

    @Override
    public void delete(Long id) {
        documentMapper.deleteById(id);
    }

    @Override
    public byte[] loadContent(Long documentId) {
        var document = documentMapper.selectById(documentId);
        return document == null ? new byte[0] : document.getContent();
    }

    @Override
    public void saveContent(Long documentId, byte[] content) {
        var update = new KnowledgeDocument();
        update.setId(documentId);
        update.setContent(content);
        documentMapper.updateById(update);
    }
}
