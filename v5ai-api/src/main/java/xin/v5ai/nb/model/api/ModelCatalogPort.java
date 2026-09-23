package xin.v5ai.nb.model.api;

import java.util.Collection;
import java.util.Map;

/**
 * 模型目录只读端口：供其它模块在**保存配置时**校验「某个模型是否可用于某项用途」。
 *
 * <p>用途是把校验提前到保存环节。以 Agent 的次要模型（{@code v5ai_agent.secondary_model_id}）
 * 为例：它指向一个非 CHAT 类型或未启用的模型时，运行期会在 {@code AgentScopeModelFactory}
 * 处抛异常，而调用它的会话标题改写与会话摘要压缩都是 **fire-and-forget 且静默失败**
 * （只打一条 debug 日志）——表现为「标题默默不再改写、摘要默默不再更新」，
 * 没有任何面向用户的报错。故此校验必须在保存时做，而不是留到运行期。</p>
 */
public interface ModelCatalogPort {

    /**
     * 该模型是否存在、类型为 CHAT 且已启用（三者同时满足才返回 true）。
     *
     * @param modelId 模型 ID；为 null 时返回 false
     */
    boolean isEnabledChatModel(Long modelId);

    /**
     * 批量读取模型展示名称，供管理端列表在服务端一次性补齐关联名称。
     *
     * @param modelIds 模型 ID 集合
     * @return 模型 ID 到展示名称的映射
     */
    default Map<Long, String> findModelNamesByIds(Collection<Long> modelIds) {
        return Map.of();
    }
}
