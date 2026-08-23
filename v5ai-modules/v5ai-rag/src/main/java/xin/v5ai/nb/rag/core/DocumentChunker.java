package xin.v5ai.nb.rag.core;

import java.util.List;

/**
 * 文档切片端口：把解析后的文档纯文本切分为可检索的切片（chunk）。
 * 切片策略影响检索召回质量与 Embedding 开销，实现可按固定长度/句段切分。
 */
public interface DocumentChunker {
    /**
     * 将文本切分为多个切片。
     *
     * @param text 解析后的文档纯文本
     * @return 切片列表
     */
    List<String> chunk(String text);
}
