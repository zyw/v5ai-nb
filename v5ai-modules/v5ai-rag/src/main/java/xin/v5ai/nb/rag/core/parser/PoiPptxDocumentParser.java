package xin.v5ai.nb.rag.core.parser;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * PPTX 解析器：基于 Apache POI {@link XMLSlideShow}，逐 slide 抽取文本形状文本。
 */
public class PoiPptxDocumentParser implements DocumentParser {
    @Override
    public String parse(byte[] content, DocumentFileType type) {
        if (type != DocumentFileType.PPTX) {
            throw new IllegalArgumentException("unsupported file type for PPTX parser: " + type);
        }
        try (var slideshow = new XMLSlideShow(new ByteArrayInputStream(content))) {
            var text = new StringBuilder();
            for (var slide : slideshow.getSlides()) {
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape && !textShape.getText().isBlank()) {
                        text.append(textShape.getText()).append('\n');
                    }
                }
            }
            return text.toString().trim();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to parse PPTX document", exception);
        }
    }
}