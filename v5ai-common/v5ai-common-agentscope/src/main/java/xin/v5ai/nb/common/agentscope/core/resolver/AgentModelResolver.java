package xin.v5ai.nb.common.agentscope.core.resolver;

import io.agentscope.core.model.Model;

/**
 * AgentDTO 模型解析器：将模型 ID 转换为实际可用的模型对象。
 *
 * 典型实现方式：根据 modelId 从数据库/配置中查询模型配置
 * （模型名称、服务商、BaseURL、API Key 等），然后构建并返回
 * io.agentscope 框架所需的 {@link Model} 实例。
 *
 * 标记为 {@link FunctionalInterface}，只含一个抽象方法，
 * 可直接用 Lambda 或方法引用实现。
 */
@FunctionalInterface
public interface AgentModelResolver {
    /**
     * 根据模型 ID 解析出对应的模型对象。
     *
     * @param modelId 模型 ID
     * @return 解析得到的模型实例
     */
    Model resolve(Long modelId);
}
