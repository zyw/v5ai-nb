package xin.v5ai.nb.runtime;

import xin.v5ai.nb.application.Application;

import java.util.List;

/**
 * 运行时 MCP Tool 解析器：根据已发布 Application 的绑定配置，
 * 解析出本次运行要注册（动态注册）的 MCP 工具集合。
 *
 * 实现约定：
 * - 只解析绑定的、处于 ACTIVE 状态的 MCP Server；
 * - DENY 权限的工具不进入结果（拒绝执行）；
 * - 单个 Server 连接失败时跳过该 Server，不中断整个运行。
 */
public interface McpToolResolver {

    /**
     * 解析 Application 绑定的 MCP 工具。
     *
     * @param application 已发布的 Application
     * @return 本次运行可用的 MCP 工具（可能为空列表）
     */
    List<ResolvedMcpTool> resolveTools(Application application);
}
