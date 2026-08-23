package xin.v5ai.nb.rag.core.parser;

import xin.v5ai.nb.rag.core.enums.DocumentFileType;

/**
 * 文档解析端口：把原始文档字节内容解析为纯文本。
 * 实现按 {@link DocumentFileType} 分发到对应解析器（TXT/Markdown/PDF/DOCX/URL）。
 */
public interface DocumentParser {
    /**
     * 解析文档内容为纯文本。
     *
     * @param content 原始文档字节
     * @param type    文档类型（决定用哪个解析器）
     * @return 解析出的纯文本
     */
    String parse(byte[] content, DocumentFileType type);
}
