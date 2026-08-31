//package xin.v5ai.nb.model.mapper;
//
//import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
//
///**
// * 模型运行时配置网关：为运行时提供"按模型 ID 查找配置"与"查找嵌入模型"的能力。
// * 由基础设施层实现（MyBatis），供 Agent 模型解析器与 RAG Embedding 客户端使用。
// */
//public interface ModelRuntimeConfigGateway {
//    /**
//     * 按模型 ID 查找运行所需的模型配置（服务商、模型名、Endpoint、密文凭据等）。
//     *
//     * @param modelId 模型 ID
//     * @return 模型运行时配置；不存在时返回 {@code null}
//     */
//    ModelRuntimeConfigDTO findRuntimeConfigByModelId(Long modelId);
//
//    /**
//     * 查找第一个已启用的 EMBEDDING 模型。
//     *
//     * @return 嵌入模型运行时配置；未配置任何启用的嵌入模型时返回 {@code null}
//     */
//    ModelRuntimeConfigDTO findFirstEmbeddingModel();
//}
