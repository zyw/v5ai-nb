package xin.v5ai.nb.rag.core.chunker;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定长度切分：按最大长度滑动窗口切分（带重叠），先折叠连续空白。
 *
 * @author ZYW
 * @since 2026-09-08
 */
@Component
public class FixedLengthSplitter {

    /**
     * @param chunkSize 最大长度（字符）
     * @param overlap   相邻窗口重叠字符数
     */
    public List<String> split(String text, int chunkSize, int overlap) {
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
}
