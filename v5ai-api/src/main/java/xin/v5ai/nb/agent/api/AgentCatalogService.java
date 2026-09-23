package xin.v5ai.nb.agent.api;

import java.util.Collection;
import java.util.Set;

/**
 * Agent 目录查询端口：供其它模块在写入前校验 Agent 是否处于「已发布」状态。
 *
 * <p>实现位于 {@code v5ai-agent}（数据归属方），避免跨模块直接读表。</p>
 */
public interface AgentCatalogService {

    /**
     * 从入参中筛出确实处于 PUBLISHED 状态的 agentKey。
     *
     * @param agentKeys 待校验的 agentKey 集合（可为空）
     * @return 其中已发布的 agentKey 集合；入参为空时返回空集合
     */
    Set<String> filterPublished(Collection<String> agentKeys);
}