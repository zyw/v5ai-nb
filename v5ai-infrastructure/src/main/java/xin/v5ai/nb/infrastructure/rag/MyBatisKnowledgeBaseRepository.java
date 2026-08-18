package xin.v5ai.nb.infrastructure.rag;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.KnowledgeBase;
import xin.v5ai.nb.rag.KnowledgeBaseOption;
import xin.v5ai.nb.rag.KnowledgeBaseRepository;
import xin.v5ai.nb.rag.KnowledgeBaseStatus;

import java.util.List;

@Repository
public class MyBatisKnowledgeBaseRepository implements KnowledgeBaseRepository {
    private final KnowledgeBaseGateway gateway;

    public MyBatisKnowledgeBaseRepository(KnowledgeBaseGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public KnowledgeBase save(KnowledgeBase knowledgeBase) {
        var entity = new KnowledgeBaseEntity();
        entity.setName(knowledgeBase.name());
        entity.setDescription(knowledgeBase.description());
        entity.setStatus(knowledgeBase.status().name());
        gateway.insertKnowledgeBase(entity);
        return new KnowledgeBase(entity.getId(), entity.getName(), entity.getDescription(),
                KnowledgeBaseStatus.valueOf(entity.getStatus()));
    }

    @Override
    public KnowledgeBase findById(Long id) {
        return toDomain(gateway.selectKnowledgeBaseById(id));
    }

    @Override
    public List<KnowledgeBase> list() {
        return gateway.selectAll().stream()
                .map(MyBatisKnowledgeBaseRepository::toDomain)
                .toList();
    }

    @Override
    public List<KnowledgeBaseOption> listOptions() {
        return gateway.selectOptions().stream()
                .map(entity -> new KnowledgeBaseOption(entity.getId(), entity.getName()))
                .toList();
    }

    @Override
    public void disable(Long id) {
        gateway.disable(id);
    }

    private static KnowledgeBase toDomain(KnowledgeBaseEntity entity) {
        if (entity == null) {
            return null;
        }
        return new KnowledgeBase(entity.getId(), entity.getName(), entity.getDescription(),
                KnowledgeBaseStatus.valueOf(entity.getStatus()));
    }
}
