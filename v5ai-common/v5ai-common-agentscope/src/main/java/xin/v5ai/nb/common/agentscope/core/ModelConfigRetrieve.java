package xin.v5ai.nb.common.agentscope.core;

import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;

/**
 * 模型运行时配置网关：为运行时提供"按模型 ID 查找配置"与"按类型选择模型"的能力。
 * 由基础设施层实现（MyBatis），供 AgentDTO 模型解析器与 RAG Embedding 客户端使用。
 */
public interface ModelConfigRetrieve {
    /**
     * 按模型 ID 查找运行所需的模型配置（服务商、模型名、Endpoint、密文凭据等）。
     *
     * @param modelId 模型 ID
     * @return 模型运行时配置；不存在时返回 {@code null}
     */
    ModelRuntimeConfigDTO findRuntimeConfigByModelId(Long modelId);

    /**
     * 按类型选择模型：优先启用的默认模型，其次按 ID 最早的启用模型。
     * 没有可用模型时抛出配置异常。
     */
    ModelRuntimeConfigDTO findDefaultOrFirstEnabledModel(String modelType);
}
