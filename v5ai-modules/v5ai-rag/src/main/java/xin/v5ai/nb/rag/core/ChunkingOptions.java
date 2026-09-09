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
 * @param chunkModelId      sliceStrategy=smart 时用于语义切片的对话模型配置 ID；为空回退按段落切分
 * @author ZYW
 * @since 2026-08-30
 */
public record ChunkingOptions(ChunkMode sliceStrategy, Integer maxChunkLength, Integer chunkOverlap,
                              String customDelimiter, String chunkRegex, boolean mergeShortSegments,
                              Long chunkModelId) {

    /**
     * 不含智能切片模型的便捷构造：{@code chunkModelId} 置空（非 smart 策略默认走此构造）。
     */
    public ChunkingOptions(ChunkMode sliceStrategy, Integer maxChunkLength, Integer chunkOverlap,
                           String customDelimiter, String chunkRegex, boolean mergeShortSegments) {
        this(sliceStrategy, maxChunkLength, chunkOverlap, customDelimiter, chunkRegex, mergeShortSegments, null);
    }

    /**
     * 空配置：使用实现默认行为。
     */
    public static final ChunkingOptions DEFAULT = new ChunkingOptions(null, null, null, null, null, false);
}
