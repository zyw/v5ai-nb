package xin.v5ai.nb.rag.core.parser;

import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;

/**
 * 文档解析请求，携带引擎选择所需的业务上下文。
 */
public record ParseRequest(
        byte[] content,
        String filename,
        DocumentFileType fileType,
        Long documentId,
        Long knowledgeBaseId,
        RagConfigDO.ParseParams params) {

    public ParseRequest {
        content = content == null ? new byte[0] : content;
        fileType = fileType == null ? DocumentFileType.TXT : fileType;
    }
}
