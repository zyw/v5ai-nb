package xin.v5ai.nb.rag.domain.bo;

import xin.v5ai.nb.rag.core.config.RagConfigDO;

/**
 * 知识库检索/问答配置更新请求体（不可变 record，供 {@code PUT /api/admin/knowledge-bases/{id}/config} 使用）。
 * <p>
 * 仅更新 {@code config} 中的 searchParams / modelParams 两块（整对象替换），
 * chunkParams / parseParams 与知识库基本信息不受影响；不做维度/向量库等全量校验。
 *
 * @param searchParams 检索参数（整对象替换；可空，空则保留原值）
 * @param modelParams  模型回答参数（整对象替换；可空，空则保留原值）
 */
public record KbConfigUpdateBo(
        RagConfigDO.SearchParams searchParams,
        RagConfigDO.ModelParams modelParams
) {
}
