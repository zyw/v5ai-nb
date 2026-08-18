package xin.v5ai.nb.rag;

import java.util.List;

public interface KnowledgeBaseRepository {
    KnowledgeBase save(KnowledgeBase knowledgeBase);

    KnowledgeBase findById(Long id);

    List<KnowledgeBase> list();

    /**
     * 知识库下拉选项轻量视图。默认基于全量列表推导，持久化实现可覆盖为投影查询以降低大表开销。
     */
    default List<KnowledgeBaseOption> listOptions() {
        return list().stream()
                .map(base -> new KnowledgeBaseOption(base.id(), base.name()))
                .toList();
    }

    void disable(Long id);
}
