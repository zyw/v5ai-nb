package xin.v5ai.nb.infrastructure.rag;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.KnowledgeTask;
import xin.v5ai.nb.rag.KnowledgeTaskRepository;
import xin.v5ai.nb.rag.TaskStatus;

import java.util.List;

@Repository
public class MyBatisKnowledgeTaskRepository implements KnowledgeTaskRepository {
    private final KnowledgeTaskGateway gateway;

    public MyBatisKnowledgeTaskRepository(KnowledgeTaskGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public KnowledgeTask save(KnowledgeTask task) {
        var entity = toEntity(task);
        if (task.id() == null) {
            gateway.insertTask(entity);
        } else {
            gateway.update(entity);
        }
        return toDomain(entity);
    }

    @Override
    public KnowledgeTask findById(Long id) {
        return toDomain(gateway.selectTaskById(id));
    }

    @Override
    public List<KnowledgeTask> findByDocumentId(Long documentId) {
        return gateway.selectByDocumentId(documentId).stream()
                .map(MyBatisKnowledgeTaskRepository::toDomain)
                .toList();
    }

    @Override
    public List<KnowledgeTask> findByKnowledgeBaseId(Long knowledgeBaseId) {
        return gateway.selectByKnowledgeBaseId(knowledgeBaseId).stream()
                .map(MyBatisKnowledgeTaskRepository::toDomain)
                .toList();
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        gateway.deleteByDocumentId(documentId);
    }

    @Override
    public List<KnowledgeTask> findProcessable(int limit) {
        return gateway.selectProcessable(limit).stream()
                .map(MyBatisKnowledgeTaskRepository::toDomain)
                .toList();
    }

    private static KnowledgeTaskEntity toEntity(KnowledgeTask task) {
        var entity = new KnowledgeTaskEntity();
        entity.setId(task.id());
        entity.setKnowledgeBaseId(task.knowledgeBaseId());
        entity.setDocumentId(task.documentId());
        entity.setStatus(task.status().name());
        entity.setAttemptCount(task.attemptCount());
        entity.setMaxAttempts(task.maxAttempts());
        entity.setErrorMessage(task.errorMessage());
        return entity;
    }

    private static KnowledgeTask toDomain(KnowledgeTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return new KnowledgeTask(entity.getId(), entity.getKnowledgeBaseId(), entity.getDocumentId(),
                TaskStatus.valueOf(entity.getStatus()), entity.getAttemptCount(), entity.getMaxAttempts(),
                entity.getErrorMessage());
    }
}
