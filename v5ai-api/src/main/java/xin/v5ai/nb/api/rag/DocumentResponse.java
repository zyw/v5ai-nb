package xin.v5ai.nb.api.rag;

import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.DocumentStatus;
import xin.v5ai.nb.rag.KnowledgeDocument;

public record DocumentResponse(
        Long id,
        Long knowledgeBaseId,
        String title,
        DocumentFileType fileType,
        String source,
        DocumentStatus status,
        String errorMessage
) {
    static DocumentResponse from(KnowledgeDocument document) {
        return new DocumentResponse(document.id(), document.knowledgeBaseId(), document.title(),
                document.fileType(), document.source(), document.status(), document.errorMessage());
    }
}
