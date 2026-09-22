package xin.v5ai.nb.rag.core;

import java.util.List;

/**
 * 文本向量化端口：将文本编码为稠密向量。
 * <p>
 * 按知识库绑定的嵌入模型（{@code embeddingModelId}）与其冻结维度（{@code frozenDimension}）
 * 执行；模型不可用/未配置时回退到确定性本地嵌入（维度 = 冻结维度或默认 1536，保证本地可跑）。
 * 可调维度模型（config {@code dimensionAdjustable=true}）请求携带 {@code dimensions=frozenDimension}，
 * 固定维度模型不带该参数、以模型实际输出为准。
 */
public interface EmbeddingClient {

    /**
     * 按指定嵌入模型与期望维度将文本编码为向量。
     *
     * @param text             输入文本
     * @param embeddingModelId 嵌入模型 ID（可为 null：使用默认 EMBEDDING 模型或最早启用模型）
     * @param frozenDimension  期望输出维度（KB 冻结维度；可调模型据此传 {@code dimensions} 参数，固定模型忽略）
     * @return 浮点向量
     */
    List<Float> embed(String text, Long embeddingModelId, Integer frozenDimension);

    /**
     * 探测某嵌入模型的实际输出维度（调一次 {@code /embeddings}，用最小输入）。
     *
     * @param embeddingModelId 嵌入模型 ID
     * @return 实际输出维度；模型不可用或调用失败时返回 {@code null}
     */
    Integer probeDimension(Long embeddingModelId);
}
