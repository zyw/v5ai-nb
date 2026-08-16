package xin.v5ai.nb.api.mcp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.mcp.McpManagementService;

import java.util.List;

/**
 * Agent 绑定 MCP Server API。
 */
@RestController
@RequestMapping("/api/admin/agents/{agentKey}/mcp-bindings")
public class AgentMcpBindingController {
    private final McpManagementService mcpManagementService;

    public AgentMcpBindingController(McpManagementService mcpManagementService) {
        this.mcpManagementService = mcpManagementService;
    }

    @PostMapping
    public void bindServers(@PathVariable("agentKey") String agentKey,
                            @RequestBody McpBindRequest request) {
        mcpManagementService.bindServers(agentKey, request.mcpServerIds());
    }

    @GetMapping
    public List<Long> getBindings(@PathVariable("agentKey") String agentKey) {
        return mcpManagementService.getServerBindings(agentKey);
    }
}
