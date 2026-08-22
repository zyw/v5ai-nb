package xin.v5ai.nb.rag;

import java.util.List;

/**
 * Embeds a text into a dense vector. Implementations may fall back to a deterministic
 * local embedding when no EMBEDDING model is configured.
 */
public interface EmbeddingClient {
    int dimensions();

    List<Float> embed(String text);
}
