package xin.v5ai.nb.rag.core;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 固定长度切片（默认 800 字符、100 重叠），支持按配置的分隔符/正则/智能（段落回退）一级切分。
 */
@Component
public class FixedSizeDocumentChunker implements DocumentChunker {
    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 100;
    private static final String DEFAULT_DELIMITER = "\n\n";

    @Override
    public List<String> chunk(String text) {
        return chunk(text, ChunkingOptions.DEFAULT);
    }

    @Override
    public List<String> chunk(String text, ChunkingOptions options) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int maxTokens = options.maxChunkTokens() == null || options.maxChunkTokens() <= 0
                ? DEFAULT_CHUNK_SIZE : options.maxChunkTokens();
        int overlap = options.chunkOverlap() == null || options.chunkOverlap() < 0
                ? DEFAULT_OVERLAP : options.chunkOverlap();
        String mode = options.mode() == null ? "" : options.mode().trim().toLowerCase();
        var segments = switch (mode) {
            case "delimiter" -> splitByDelimiter(text, options.customDelimiter());
            case "regex" -> splitByRegex(text, options.chunkRegex());
            // 智能切片需对话模型语义切分；未配置模型调用时回退为按段落粗分再递归切分
            case "smart" -> splitByParagraphs(text);
            default -> List.of(text);
        };
        List<String> chunks = new ArrayList<>();
        for (String segment : segments) {
            chunks.addAll(splitByLength(segment, maxTokens, overlap));
        }
        if (options.mergeShortSegments()) {
            chunks = mergeShortSegments(chunks, maxTokens);
        }
        return chunks;
    }

    /**
     * 按分隔符做一级切分（分隔符按字面量匹配），再逐段做最大长度递归切分。
     */
    private List<String> splitByDelimiter(String text, String delimiter) {
        // 分隔符可能是纯空白（如 \n\n），不能用 isBlank 判空
        String delim = StrUtil.isEmpty(delimiter) ? DEFAULT_DELIMITER : delimiter;
        return Arrays.stream(text.split(Pattern.quote(delim), -1))
                .filter(s -> !s.isBlank())
                .toList();
    }

    /**
     * 按 Java 正则做一级切分。
     */
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

    /**
     * 按空行划分段落（智能切分的回退路径）。
     */
    private List<String> splitByParagraphs(String text) {
        return Arrays.stream(text.split("\\n\\s*\\n"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    /**
     * 固定窗口长度切分，带重叠；先折叠连续空白。
     */
    private List<String> splitByLength(String text, int chunkSize, int overlap) {
        var normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= chunkSize) {
            return List.of(normalized);
        }
        var chunks = new ArrayList<String>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(start + chunkSize - overlap, start + 1);
        }
        return chunks;
    }

    /**
     * 把长度不足最大长度一半的片段并入其后片段（末尾短片段并入最后一段）。
     */
    private List<String> mergeShortSegments(List<String> chunks, int maxTokens) {
        if (chunks.size() < 2) {
            return chunks;
        }
        int threshold = Math.max(1, maxTokens / 2);
        var merged = new ArrayList<String>();
        StringBuilder pending = new StringBuilder();
        for (String chunk : chunks) {
            if (chunk.length() < threshold) {
                pending.append(pending.isEmpty() ? "" : "\n\n").append(chunk);
            } else {
                merged.add(pending.isEmpty() ? chunk : pending + "\n\n" + chunk);
                pending.setLength(0);
            }
        }
        if (!pending.isEmpty()) {
            if (!merged.isEmpty()) {
                int last = merged.size() - 1;
                merged.set(last, merged.get(last) + "\n\n" + pending);
            } else {
                merged.add(pending.toString());
            }
        }
        return merged;
    }
}
