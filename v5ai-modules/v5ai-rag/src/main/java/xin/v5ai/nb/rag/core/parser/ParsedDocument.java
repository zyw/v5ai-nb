package xin.v5ai.nb.rag.core.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 结构化文档解析结果。切片输入由 {@link #content()} 统一选择。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {

    private String engine;
    private String text;
    private String markdown;
    private String structuredJson;
    @Builder.Default
    private List<ParsedImage> images = new ArrayList<>();
    @Builder.Default
    private List<ParsedLocator> locators = new ArrayList<>();
    private ParseDiagnostics diagnostics;

    public String content() {
        if (text != null && !text.isBlank()) {
            return text;
        }
        return markdown == null ? "" : markdown;
    }

    public boolean isBlank() {
        return content().isBlank();
    }

    public record ParsedImage(
            String name,
            String mimeType,
            byte[] bytes,
            String altText,
            Integer pageNumber,
            String locator) {
    }

    public record ParsedLocator(
            int startOffset,
            int endOffset,
            Integer pageNumber,
            String blockType,
            String sourceId) {
    }

    public record ParseDiagnostics(
            String status,
            long elapsedMillis,
            List<String> warnings,
            Map<String, Object> raw) {
    }
}
