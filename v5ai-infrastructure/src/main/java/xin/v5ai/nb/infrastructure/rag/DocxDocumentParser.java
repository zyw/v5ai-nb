package xin.v5ai.nb.infrastructure.rag;

import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.DocumentParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipInputStream;

/**
 * Lightweight DOCX parser: reads {@code word/document.xml} from the OOXML zip
 * container and strips XML tags. Avoids a heavy Apache POI dependency.
 */
public class DocxDocumentParser implements DocumentParser {
    @Override
    public String parse(byte[] content, DocumentFileType type) {
        if (type != DocumentFileType.DOCX) {
            throw new IllegalArgumentException("unsupported file type for DOCX parser: " + type);
        }
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    var xml = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    return stripTags(xml);
                }
                entry = zip.getNextEntry();
            }
            throw new IllegalArgumentException("docx document.xml not found");
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to parse DOCX document", exception);
        }
    }

    private String stripTags(String xml) {
        var withoutTags = xml.replaceAll("<[^>]+>", " ");
        return withoutTags
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }
}
