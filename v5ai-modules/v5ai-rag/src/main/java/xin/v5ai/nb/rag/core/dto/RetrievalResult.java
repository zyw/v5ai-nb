package xin.v5ai.nb.rag.core.dto;

import xin.v5ai.nb.rag.core.store.VectorStore;

import java.util.List;

public record RetrievalResult(String context, List<VectorStore.RetrievalHit> hits) {
    public static RetrievalResult empty() {
        return new RetrievalResult("", List.of());
    }

    public boolean isEmpty() {
        return context == null || context.isBlank();
    }
}
