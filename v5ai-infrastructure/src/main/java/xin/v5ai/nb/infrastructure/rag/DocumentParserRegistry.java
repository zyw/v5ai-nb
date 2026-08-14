package xin.v5ai.nb.infrastructure.rag;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.DocumentParser;

import java.util.EnumMap;
import java.util.Map;

/**
 * Routes parsing to the right implementation by document file type.
 */
@Primary
@Component
public class DocumentParserRegistry implements DocumentParser {
    private final Map<DocumentFileType, DocumentParser> parsers = new EnumMap<>(DocumentFileType.class);

    public DocumentParserRegistry() {
        parsers.put(DocumentFileType.TXT, new TextDocumentParser());
        parsers.put(DocumentFileType.MARKDOWN, new TextDocumentParser());
        parsers.put(DocumentFileType.URL, new TextDocumentParser());
        parsers.put(DocumentFileType.PDF, new PdfBoxDocumentParser());
        parsers.put(DocumentFileType.DOCX, new DocxDocumentParser());
    }

    @Override
    public String parse(byte[] content, DocumentFileType type) {
        var parser = parsers.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("no parser for file type: " + type);
        }
        return parser.parse(content, type);
    }
}
