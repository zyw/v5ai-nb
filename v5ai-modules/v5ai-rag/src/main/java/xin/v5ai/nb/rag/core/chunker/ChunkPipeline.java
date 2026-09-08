package xin.v5ai.nb.rag.core.chunker;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * 切片流水线：逐段按最大长度固定切分（带重叠），并按需合并短片段。
 * 所有切片策略共用同一流水线，策略实现类只需提供一级切分产物（候选片段）。
 *
 * @author ZYW
 * @since 2026-09-08
 */
@Component
public class ChunkPipeline {
    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 100;

    private final FixedLengthSplitter splitter;
    private final ChunkMerger merger;

    public ChunkPipeline(FixedLengthSplitter splitter, ChunkMerger merger) {
        this.splitter = splitter;
        this.merger = merger;
    }

    public List<String> run(List<String> segments, ChunkingOptions options) {
        int maxLength = options.maxChunkLength() == null || options.maxChunkLength() <= 0
                ? DEFAULT_CHUNK_SIZE : options.maxChunkLength();
        int overlap = options.chunkOverlap() == null || options.chunkOverlap() < 0
                ? DEFAULT_OVERLAP : options.chunkOverlap();
        var chunks = new ArrayList<String>();
        for (String segment : segments) {
            chunks.addAll(splitter.split(segment, maxLength, overlap));
        }
        return options.mergeShortSegments() ? merger.merge(chunks, maxLength) : chunks;
    }
}
