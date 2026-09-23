package xin.v5ai.nb.model.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.vo.V5aiModelVo;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Mapper
public interface V5aiModelMapper extends BaseMapperPlus<V5aiModel, V5aiModelVo> {
    /**
     * 统计引用该模型的 AgentDTO 数（任意状态）。
     *
     * @param modelId 模型 ID
     * @return 引用该模型的 AgentDTO 行数
     */
    Long countAgentUsage(Long modelId);

    /**
     * 统计引用该模型的知识库数（embedding_model_id / rerank_model_id 任一命中即计入）。
     *
     * @param modelId 模型 ID
     * @return 引用该模型的知识库行数
     */
    Long countKnowledgeBaseUsage(Long modelId);

    /**
     * 清除同 {@code modelType} 下其它模型的默认标记（保留 {@code keepId}）。
     *
     * @param modelType 模型类型（CHAT/EMBEDDING/RERANK）
     * @param keepId    要保留默认标记的模型 ID（目标模型）
     */
    void clearModelTypeDefaults(String modelType, Long keepId);
}
