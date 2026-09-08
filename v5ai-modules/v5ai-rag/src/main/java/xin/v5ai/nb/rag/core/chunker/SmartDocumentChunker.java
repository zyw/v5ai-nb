package xin.v5ai.nb.rag.core.chunker;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.util.Arrays;
import java.util.List;

/**
 * 智能切片：由对话模型将全文切分为语义候选片段，再经共享流水线按最大长度切分。
 * 当前未接入对话模型调用，回退为按空行划分段落（与按长度切片的一级切分一致）；
 * 接入后在此注入模型切分逻辑（对应 config.chunkParams.chunkModelId）。
 *
 * @author ZYW
 * @since 2026-09-08
 */
@Component
public class SmartDocumentChunker implements ChunkingStrategy {
    private final ChunkPipeline pipeline;

    public SmartDocumentChunker(ChunkPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public ChunkMode supportedMode() {
        return ChunkMode.SMART;
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
