package xin.v5ai.nb.infrastructure.rag;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.DocumentStatus;
import xin.v5ai.nb.rag.KnowledgeDocument;
import xin.v5ai.nb.rag.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.KnowledgeDocumentRepository;

import java.util.List;

@Repository
public class MyBatisKnowledgeDocumentRepository implements KnowledgeDocumentRepository, KnowledgeDocumentContentStore {
    private final KnowledgeDocumentGateway gateway;

    public MyBatisKnowledgeDocumentRepository(KnowledgeDocumentGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        var entity = toEntity(document);
        if (document.id() == null) {
            gateway.insertDocument(entity);
        } else {
            gateway.update(entity);
        }
        return new KnowledgeDocument(entity.getId(), entity.getKnowledgeBaseId(), entity.getTitle(),
                DocumentFileType.valueOf(entity.getFileType()), entity.getSource(),
                DocumentStatus.valueOf(entity.getStatus()), entity.getErrorMessage());
    }

    @Override
    public KnowledgeDocument findById(Long id) {
        return toDomain(gateway.selectKnowledgeDocumentById(id));
    }

    @Override
    public List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId) {
        return gateway.selectByKnowledgeBaseId(knowledgeBaseId).stream()
                .map(MyBatisKnowledgeDocumentRepository::toDomain)
                .toList();
    }

    @Override
    public void delete(Long id) {
        gateway.deleteById(id);
    }

    @Override
    public byte[] loadContent(Long documentId) {
        var entity = gateway.selectKnowledgeDocumentById(documentId);
        return entity == null ? new byte[0] : entity.getContent();
    }

    @Override
    public void saveContent(Long documentId, byte[] content) {
        gateway.updateContent(documentId, content);
    }

    private static KnowledgeDocumentEntity toEntity(KnowledgeDocument document) {
        var entity = new KnowledgeDocumentEntity();
        entity.setId(document.id());
        entity.setKnowledgeBaseId(document.knowledgeBaseId());
        entity.setTitle(document.title());
        entity.setFileType(document.fileType().name());
        entity.setSource(document.source());
        entity.setStatus(document.status().name());
        entity.setErrorMessage(document.errorMessage());
        return entity;
    }

    private static KnowledgeDocument toDomain(KnowledgeDocumentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new KnowledgeDocument(entity.getId(), entity.getKnowledgeBaseId(), entity.getTitle(),
                DocumentFileType.valueOf(entity.getFileType()), entity.getSource(),
                DocumentStatus.valueOf(entity.getStatus()), entity.getErrorMessage());
    }
}
