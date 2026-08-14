package xin.v5ai.nb.api.rag;

import xin.v5ai.nb.rag.KnowledgeBase;
import xin.v5ai.nb.rag.KnowledgeBaseStatus;

public record KnowledgeBaseResponse(Long id, String name, String description, KnowledgeBaseStatus status) {
    static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(knowledgeBase.id(), knowledgeBase.name(),
                knowledgeBase.description(), knowledgeBase.status());
    }
}
