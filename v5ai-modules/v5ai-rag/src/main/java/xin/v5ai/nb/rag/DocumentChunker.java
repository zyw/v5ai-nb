package xin.v5ai.nb.rag;

import java.util.List;

/**
 * Splits parsed document text into searchable chunks.
 */
public interface DocumentChunker {
    List<String> chunk(String text);
}
