package xin.v5ai.nb.rag;

public record KnowledgeBase(Long id, String name, String description, KnowledgeBaseStatus status) {
    public KnowledgeBase(String name, String description) {
        this(null, name, description, KnowledgeBaseStatus.ACTIVE);
    }
}
