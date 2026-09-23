package xin.v5ai.nb.rag.core.chunker;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 正则切片：先按 Java 正则对全文做一级切分（Pattern 语法），再经共享流水线按最大长度切分。
 *
 * @author ZYW
 * @since 2026-09-08
 */
@Component
public class RegexDocumentChunker implements ChunkingStrategy {
    private final ChunkPipeline pipeline;

    public RegexDocumentChunker(ChunkPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public ChunkMode supportedMode() {
        return ChunkMode.REGEX;
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
        return pipeline.run(splitByRegex(text, options.chunkRegex()), options);
    }

    private List<String> splitByRegex(String text, String regex) {
        if (StrUtil.isBlank(regex)) {
            throw new IllegalArgumentException("正则切片必须配置一级切分正则");
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("切片正则不合法: " + e.getMessage(), e);
        }
        return Arrays.stream(pattern.split(text))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
