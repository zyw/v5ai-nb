package xin.v5ai.nb.mcp.core;

import xin.v5ai.nb.common.agentscope.core.McpConnection;
import xin.v5ai.nb.mcp.core.domain.dto.McpServerDTO;

/**
 * 根据 MCP Server 配置创建 {@link McpConnection} 的工厂。
 * 实现方负责传输层细节（Streamable HTTP / SSE / Stdio）与超时控制。
 */
public interface McpClientFactory {

    /**
     * 建立连接。连接建立失败（无法初始化）时抛出异常，由调用方决定降级策略。
     */
    McpConnection connect(McpServerDTO server);
}
