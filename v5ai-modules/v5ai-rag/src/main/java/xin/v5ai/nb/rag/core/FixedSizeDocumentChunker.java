package xin.v5ai.nb.rag.core;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-size chunking with overlap (default 800 chars, 100 overlap).
 */
@Component
public class FixedSizeDocumentChunker implements DocumentChunker {
    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 100;

    @Override
    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= chunkSize) {
            return List.of(normalized);
        }
        var chunks = new ArrayList<String>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(start + chunkSize - overlap, start + 1);
        }
        return chunks;
    }
}
