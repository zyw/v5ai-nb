package xin.v5ai.nb.rag.core.chunker;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 短片段合并：把长度不足最大长度一半的片段并入其后片段（末尾短片段并入最后一段）。
 *
 * @author ZYW
 * @since 2026-09-08
 */
@Component
public class ChunkMerger {

    public List<String> merge(List<String> chunks, int maxLength) {
        if (chunks.size() < 2) {
            return chunks;
        }
        int threshold = Math.max(1, maxLength / 2);
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
