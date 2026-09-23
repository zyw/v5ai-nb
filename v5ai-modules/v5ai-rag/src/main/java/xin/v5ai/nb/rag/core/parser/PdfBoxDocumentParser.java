package xin.v5ai.nb.rag.core.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;

import java.io.IOException;
import java.io.UncheckedIOException;

public class PdfBoxDocumentParser implements DocumentParser {
    @Override
    public String parse(byte[] content, DocumentFileType type) {
        if (type != DocumentFileType.PDF) {
            throw new IllegalArgumentException("unsupported file type for PDF parser: " + type);
        }
        try (var document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to parse PDF document", exception);
        }
    }
}
