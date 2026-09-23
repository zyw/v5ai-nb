package xin.v5ai.nb.rag.core.chunker;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 按分隔符切片：先按所选分隔符做一级切分（多分隔符为「任一命中即切」），再经共享流水线按最大长度切分。
 * 分隔符以 JSON 字符串数组编码（如 {@code ["\n\n","。"]}）；非法/空回退默认 {@code "\n\n"}。
 *
 * @author ZYW
 * @since 2026-09-08
 */
@Component
public class DelimiterDocumentChunker implements ChunkingStrategy {
    private static final String DEFAULT_DELIMITER = "\n\n";

    private final ChunkPipeline pipeline;

    public DelimiterDocumentChunker(ChunkPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public ChunkMode supportedMode() {
        return ChunkMode.DELIMITER;
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
        return pipeline.run(splitByDelimiter(text, options.customDelimiter()), options);
    }

    private List<String> splitByDelimiter(String text, String delimiterSpec) {
        List<String> delimiters = parseDelimiters(delimiterSpec);
        if (delimiters.isEmpty()) {
            delimiters = List.of(DEFAULT_DELIMITER);
        }
        String alternation = delimiters.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Arrays.stream(text.split(alternation, -1))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<String> parseDelimiters(String spec) {
        if (StrUtil.isEmpty(spec)) {
            return List.of();
        }
        try {
            var array = JSONUtil.parseArray(spec);
            var result = new ArrayList<String>();
            for (var item : array) {
                if (item != null && !item.toString().isEmpty()) {
                    result.add(item.toString());
                }
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }
}
