package xin.v5ai.nb.rag.core.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * DOCX 解析器：基于 Apache POI {@link XWPFDocument}，抽取段落与表格文本。
 */
public class DocxDocumentParser implements DocumentParser {
    @Override
    public String parse(byte[] content, DocumentFileType type) {
        if (type != DocumentFileType.DOCX) {
            throw new IllegalArgumentException("unsupported file type for DOCX parser: " + type);
        }
        try (var document = new XWPFDocument(new ByteArrayInputStream(content))) {
            var text = new StringBuilder();
            for (var paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append('\n');
            }
            for (var table : document.getTables()) {
                for (var row : table.getRows()) {
                    for (var cell : row.getTableCells()) {
                        text.append(cell.getText()).append('\t');
                    }
                    text.append('\n');
                }
            }
            return text.toString().trim();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to parse DOCX document", exception);
        }
    }
}