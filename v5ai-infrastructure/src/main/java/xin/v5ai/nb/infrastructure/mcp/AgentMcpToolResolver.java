package xin.v5ai.nb.infrastructure.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.mcp.AgentMcpBindingRepository;
import xin.v5ai.nb.mcp.McpClientFactory;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpServerRepository;
import xin.v5ai.nb.mcp.McpToolDefinition;
import xin.v5ai.nb.mcp.McpToolPermission;
import xin.v5ai.nb.mcp.McpToolRepository;
import xin.v5ai.nb.runtime.McpToolResolver;
import xin.v5ai.nb.runtime.ResolvedMcpTool;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时 MCP Tool 解析器实现：
 * 读取 Agent 绑定的 ACTIVE MCP Server 的已缓存 Tool（权限过滤 DENY），
 * 并为其建立本次运行的隔离连接。单个 Server 连接失败时跳过，不影响整体运行。
 */
@Component
public class AgentMcpToolResolver implements McpToolResolver {
    private static final Logger log = LoggerFactory.getLogger(AgentMcpToolResolver.class);

    private final AgentMcpBindingRepository bindingRepository;
    private final McpServerRepository serverRepository;
    private final McpToolRepository toolRepository;
    private final McpClientFactory clientFactory;

    public AgentMcpToolResolver(
            AgentMcpBindingRepository bindingRepository,
            McpServerRepository serverRepository,
            McpToolRepository toolRepository,
            McpClientFactory clientFactory
    ) {
        this.bindingRepository = bindingRepository;
        this.serverRepository = serverRepository;
        this.toolRepository = toolRepository;
        this.clientFactory = clientFactory;
    }

    @Override
    public List<ResolvedMcpTool> resolveTools(Agent application) {
        var serverIds = bindingRepository.findByAgentKey(application.agentKey());
        if (serverIds.isEmpty()) {
            return List.of();
        }
        var resolved = new ArrayList<ResolvedMcpTool>();
        for (Long serverId : serverIds) {
            resolveServer(application, serverId, resolved);
        }
        return resolved;
    }

    private void resolveServer(Agent application, Long serverId, List<ResolvedMcpTool> resolved) {
        var server = serverRepository.findById(serverId);
        if (server == null || !server.isActive()) {
            log.debug("MCP server {} is missing or disabled, skipped for app {}", serverId, application.agentKey());
            return;
        }
        List<McpToolDefinition> tools = toolRepository.findByServerId(serverId).stream()
                .filter(tool -> tool.permission() != McpToolPermission.DENY)
                .toList();
        if (tools.isEmpty()) {
            log.info("MCP server '{}' has no usable tools (discover tools first, or all denied)", server.name());
            return;
        }
        McpConnection connection;
        try {
            connection = clientFactory.connect(server);
        } catch (Exception exception) {
            log.warn("MCP server '{}' unavailable at runtime, skipped: {}",
                    server.name(), exception.getMessage());
            return;
        }
        for (McpToolDefinition tool : tools) {
            resolved.add(new ResolvedMcpTool(
                    server.id(),
                    server.name(),
                    tool.toolName(),
                    tool.description(),
                    tool.inputSchema(),
                    tool.permission(),
                    connection));
        }
    }
}
