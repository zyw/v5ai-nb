package xin.v5ai.nb.rag.domain.bo;

/**
 * 知识库检索调试请求体（不可变 record）。
 * <p>
 * 空字段表示「沿用知识库已存配置（RagConfigDO.SearchParams），再无配置时用默认值」，
 * 本页参数仅本次调试生效、不回写。
 *
 * @param query            检索问题（必填）
 * @param resultCount      结果返回数量（默认 20）
 * @param questionRewrite  是否启用问题改写（需可用的对话模型；模型缺失时自动跳过）
 * @param thresholdEnabled 是否启用阈值过滤
 * @param threshold        相似度阈值（0~1，仅向量路得分参与过滤）
 * @param fusionStrategy   融合策略：RRF / WEIGHTED_SUM / VECTOR / KEYWORD（默认 RRF）
 * @param rrfK             RRF 融合 K 值（默认 60）
 * @param modelId          问题改写所用对话模型 ID（可空，回退知识库 ModelParams.modelId）
 * @param denseWeight      WEIGHTED_SUM 融合时稠密向量路权重（0~1，默认 0.5）
 */
public record KbRetrieveBo(
        String query,
        Integer resultCount,
        Boolean questionRewrite,
        Boolean thresholdEnabled,
        Double threshold,
        String fusionStrategy,
        Integer rrfK,
        Long modelId,
        Double denseWeight
) {
}
