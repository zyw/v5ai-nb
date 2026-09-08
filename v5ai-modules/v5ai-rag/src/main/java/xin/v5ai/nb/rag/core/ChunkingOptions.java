package xin.v5ai.nb.rag.core;

/**
 * 文档切片参数（来自知识库 config.chunkParams 与 delimiter 列）。
 *
 * @param sliceStrategy     切片策略 length | delimiter | regex | smart；为空时按 length 处理
 * @param maxChunkLength    单片段最大字符数；为空使用实现默认
 * @param chunkOverlap      片段重叠字符数；为空使用实现默认
 * @param customDelimiter   sliceStrategy=delimiter 时的一级切分符
 * @param chunkRegex        sliceStrategy=regex 时的一级切分正则（Java Pattern）
 * @param mergeShortSegments 是否将过短片段并入相邻片段
 * @author ZYW
 * @since 2026-08-30
 */
public record ChunkingOptions(ChunkMode sliceStrategy, Integer maxChunkLength, Integer chunkOverlap,
                              String customDelimiter, String chunkRegex, boolean mergeShortSegments) {

    /**
     * 空配置：使用实现默认行为。
     */
    public static final ChunkingOptions DEFAULT = new ChunkingOptions(null, null, null, null, null, false);
}
