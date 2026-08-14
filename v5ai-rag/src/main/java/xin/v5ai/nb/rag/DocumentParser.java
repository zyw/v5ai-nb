package xin.v5ai.nb.rag;

/**
 * Parses raw document content into plain text.
 */
public interface DocumentParser {
    String parse(byte[] content, DocumentFileType type);
}
