package xin.v5ai.nb.common.agentscope.core.resolver;

import xin.v5ai.nb.common.agentscope.core.domain.ResolvedMcpTool;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;

import java.util.Collection;
import java.util.List;

/**
 * 运行时 MCP Tool 解析器：根据已发布 AgentDTO 的绑定配置，
 * 解析出本次运行要注册（动态注册）的 MCP 工具集合。
 *
 * 实现约定：
 * - 只解析绑定的、处于 ACTIVE 状态的 MCP Server；
 * - DENY 权限的工具不进入结果（拒绝执行）；
 * - 调用方显式禁用的 Server 直接跳过（**在建立连接之前**跳过，避免连上又立刻关掉）；
 * - 单个 Server 连接失败时跳过该 Server，不中断整个运行。
 */
public interface McpToolResolver {

    /**
     * 解析 AgentDTO 绑定的 MCP 工具。
     *
     * @param application       已发布的 AgentDTO
     * @param disabledServerIds 本次运行要收窄掉的 MCP Server（只能减少绑定，不能增加）；
     *                          空集合表示不额外收窄
     * @return 本次运行可用的 MCP 工具（可能为空列表）
     */
    List<ResolvedMcpTool> resolveTools(AgentDTO application, Collection<Long> disabledServerIds);

    /**
     * 不做额外收窄的便捷重载。
     */
    default List<ResolvedMcpTool> resolveTools(AgentDTO application) {
        return resolveTools(application, List.of());
    }
}
