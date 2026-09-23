package xin.v5ai.nb.rag.core.chunker;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.util.Arrays;
import java.util.List;

/**
 * 按长度切片：先按空行粗分段落，再经共享流水线按最大长度切分（带重叠），短段落保持独立。
 *
 * @author ZYW
 * @since 2026-09-08
 */
@Component
public class LengthDocumentChunker implements ChunkingStrategy {
    private final ChunkPipeline pipeline;

    public LengthDocumentChunker(ChunkPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public ChunkMode supportedMode() {
        return ChunkMode.LENGTH;
    }

    @Override
    public List<String> chunk(String text) {
        return chunk(text, ChunkingOptions.DEFAULT);
    }

    @Override
    public List<String> chunk(String text, ChunkingOptions options) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        return pipeline.run(splitByParagraphs(text), options);
    }

    private List<String> splitByParagraphs(String text) {
        return Arrays.stream(text.split("\\n\\s*\\n"))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
