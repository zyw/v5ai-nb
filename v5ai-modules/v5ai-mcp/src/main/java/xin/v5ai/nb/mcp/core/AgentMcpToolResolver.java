package xin.v5ai.nb.mcp.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.McpConnection;
import xin.v5ai.nb.common.agentscope.utils.McpJsonCodec;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedMcpTool;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.resolver.McpToolResolver;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;
import xin.v5ai.nb.mcp.core.domain.dto.McpToolDTO;
import xin.v5ai.nb.mcp.core.service.McpServerService;
import xin.v5ai.nb.mcp.core.service.McpToolService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 运行时 MCP Tool 解析器实现：
 * 读取 AgentDTO 绑定的 ACTIVE MCP Server 的已缓存 Tool（权限过滤 DENY），
 * 并为其建立本次运行的隔离连接。单个 Server 连接失败时跳过，不影响整体运行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMcpToolResolver implements McpToolResolver {

    private final AgentMcpBindingService bindingService;
    private final McpServerService serverService;
    private final McpToolService toolService;
    private final McpClientFactory clientFactory;

    @Override
    public List<ResolvedMcpTool> resolveTools(AgentDTO application, Collection<Long> disabledServerIds) {
        var bindings = bindingService.selectList(application.agentKey());
        if (bindings.isEmpty()) {
            return List.of();
        }
        var disabled = disabledServerIds == null ? Set.<Long>of() : Set.copyOf(disabledServerIds);
        var resolved = new ArrayList<ResolvedMcpTool>();
        for (var binding : bindings) {
            resolveServer(application, binding.getMcpServerId(), disabled, resolved);
        }
        return resolved;
    }

    private void resolveServer(AgentDTO application, Long serverId, Set<Long> disabledServerIds,
                               List<ResolvedMcpTool> resolved) {
        // 收窄项在建立连接之前生效：连上再关掉会白白产生一次外部副作用
        if (disabledServerIds.contains(serverId)) {
            log.debug("MCP server {} disabled for this run, skipped for app {}", serverId, application.agentKey());
            return;
        }
        var server = serverService.selectById(serverId);
        if (server == null || !"ACTIVE".equals(server.getStatus())) {
            log.debug("MCP server {} is missing or disabled, skipped for app {}", serverId, application.agentKey());
            return;
        }
        List<McpToolDTO> tools = toolService.selectList(serverId)
                .stream()
                .filter(tool -> !McpToolPermission.DENY.name().equals(tool.getPermission()))
                .toList();
        if (tools.isEmpty()) {
            log.info("MCP server '{}' has no usable tools (discover tools first, or all denied)", server.getName());
            return;
        }
        McpConnection connection;
        try {
            connection = clientFactory.connect(server);
        } catch (Exception exception) {
            log.warn("MCP server '{}' unavailable at runtime, skipped: {}",
                    server.getName(), exception.getMessage());
            return;
        }
        for (McpToolDTO tool : tools) {
            resolved.add(new ResolvedMcpTool(
                    server.getId(),
                    server.getName(),
                    tool.getToolName(),
                    tool.getDescription(),
                    McpJsonCodec.toObjectMap(tool.getInputSchemaJson()),
                    McpToolPermission.valueOf(tool.getPermission()),
                    connection));
        }
    }
}
