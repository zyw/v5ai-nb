package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.McpClientFactory;
import xin.v5ai.nb.common.agentscope.core.McpToolResolver;

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

    private final AgentMcpBindingMapper bindingMapper;
    private final McpServerMapper serverMapper;
    private final McpToolMapper toolMapper;
    private final McpClientFactory clientFactory;

    public AgentMcpToolResolver(
            AgentMcpBindingMapper bindingMapper,
            McpServerMapper serverMapper,
            McpToolMapper toolMapper,
            McpClientFactory clientFactory
    ) {
        this.bindingMapper = bindingMapper;
        this.serverMapper = serverMapper;
        this.toolMapper = toolMapper;
        this.clientFactory = clientFactory;
    }

    @Override
    public List<ResolvedMcpTool> resolveTools(Agent application) {
        var bindings = bindingMapper.selectList(new LambdaQueryWrapper<AgentMcpBinding>()
                .eq(AgentMcpBinding::getAgentKey, application.agentKey()));
        if (bindings.isEmpty()) {
            return List.of();
        }
        var resolved = new ArrayList<ResolvedMcpTool>();
        for (var binding : bindings) {
            resolveServer(application, binding.getMcpServerId(), resolved);
        }
        return resolved;
    }

    private void resolveServer(Agent application, Long serverId, List<ResolvedMcpTool> resolved) {
        var server = serverMapper.selectById(serverId);
        if (server == null || !"ACTIVE".equals(server.getStatus())) {
            log.debug("MCP server {} is missing or disabled, skipped for app {}", serverId, application.agentKey());
            return;
        }
        List<McpTool> tools = toolMapper.selectList(new LambdaQueryWrapper<McpTool>()
                        .eq(McpTool::getServerId, serverId))
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
        for (McpTool tool : tools) {
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
