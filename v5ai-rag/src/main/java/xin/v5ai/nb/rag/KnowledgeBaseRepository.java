package xin.v5ai.nb.rag;

import java.util.List;

public interface KnowledgeBaseRepository {
    KnowledgeBase save(KnowledgeBase knowledgeBase);

    KnowledgeBase findById(Long id);

    List<KnowledgeBase> list();

    void disable(Long id);
}
