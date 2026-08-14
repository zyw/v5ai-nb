package xin.v5ai.nb.infrastructure.rag;

import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.DocumentParser;

import java.nio.charset.StandardCharsets;

public class TextDocumentParser implements DocumentParser {
    @Override
    public String parse(byte[] content, DocumentFileType type) {
        if (type != DocumentFileType.TXT && type != DocumentFileType.MARKDOWN && type != DocumentFileType.URL) {
            throw new IllegalArgumentException("unsupported text file type: " + type);
        }
        return new String(content, StandardCharsets.UTF_8);
    }
}
