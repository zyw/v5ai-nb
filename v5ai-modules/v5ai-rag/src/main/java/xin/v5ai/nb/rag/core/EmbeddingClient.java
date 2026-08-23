package xin.v5ai.nb.rag.core;

import java.util.List;

/**
 * 文本向量化端口：将文本编码为稠密向量。
 * 实现通常调用 EMBEDDING 模型；当未配置嵌入模型时，
 * 可回退到确定性本地嵌入（保证向量维度与稳定性，便于本地验证）。
 */
public interface EmbeddingClient {
    /**
     * 向量维度（如 1536），用于创建向量列与索引。
     */
    int dimensions();

    /**
     * 将文本编码为向量。
     *
     * @param text 输入文本
     * @return 浮点向量（长度为 {@link #dimensions()}）
     */
    List<Float> embed(String text);
}
