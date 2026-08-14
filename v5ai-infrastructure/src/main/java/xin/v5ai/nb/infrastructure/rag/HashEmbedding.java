package xin.v5ai.nb.infrastructure.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic development embedding: hashes the text into a fixed-size vector.
 * Not semantically meaningful, but keeps the RAG pipeline runnable end-to-end
 * without a configured EMBEDDING model.
 */
public final class HashEmbedding {
    private HashEmbedding() {
    }

    public static List<Float> embed(String text, int dimensions) {
        var bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        var values = new ArrayList<Float>(dimensions);
        long seed = 0x9E3779B97F4A7C15L;
        for (byte b : bytes) {
            seed ^= (b & 0xFFL) + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2);
        }
        for (int i = 0; i < dimensions; i++) {
            long h = seed ^ (i * 0x9E3779B97F4A7C15L);
            h = mix(h);
            values.add(normalize(h));
        }
        return values;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float normalize(long h) {
        return (h & 0xFFFF_FFFFL) / (float) 0xFFFF_FFFFL * 2.0f - 1.0f;
    }
}
