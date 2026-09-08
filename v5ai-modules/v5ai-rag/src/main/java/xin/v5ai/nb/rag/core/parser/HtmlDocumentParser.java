package xin.v5ai.nb.rag.core.parser;

import org.jsoup.Jsoup;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;

import java.nio.charset.StandardCharsets;

/**
 * HTML 解析器：基于 Jsoup 抽取网页正文文本（剔除标签/脚本/样式）。
 */
public class HtmlDocumentParser implements DocumentParser {
    @Override
    public String parse(byte[] content, DocumentFileType type) {
        if (type != DocumentFileType.HTML) {
            throw new IllegalArgumentException("unsupported file type for HTML parser: " + type);
        }
        var html = new String(content, StandardCharsets.UTF_8);
        return Jsoup.parse(html).text();
    }
}