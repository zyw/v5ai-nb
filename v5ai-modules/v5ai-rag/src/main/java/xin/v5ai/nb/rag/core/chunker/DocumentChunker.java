package xin.v5ai.nb.rag.core.chunker;

import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.util.List;

/**
 * 文档切片接口：把解析后的文档纯文本切分为可检索的切片（chunk）。
 * 切片策略影响检索召回质量与 Embedding 开销，实现可按固定长度/分隔符/正则/智能切分。
 */
public interface DocumentChunker {

    /**
     * 使用默认参数将文本切分为多个切片。
     *
     * @param text 解析后的文档纯文本
     * @return 切片列表
     */
    List<String> chunk(String text);

    /**
     * 按知识库配置的切片参数切分文本；配置缺省字段回退到实现默认。
     *
     * @param text    解析后的文档纯文本
     * @param options 切片参数
     * @return 切片列表
     */
    default List<String> chunk(String text, ChunkingOptions options) {
        return chunk(text);
    }
}
