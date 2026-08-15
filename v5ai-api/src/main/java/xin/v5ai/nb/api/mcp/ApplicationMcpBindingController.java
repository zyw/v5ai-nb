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
 * Application 绑定 MCP Server API。
 */
@RestController
@RequestMapping("/api/admin/applications/{appKey}/mcp-bindings")
public class ApplicationMcpBindingController {
    private final McpManagementService mcpManagementService;

    public ApplicationMcpBindingController(McpManagementService mcpManagementService) {
        this.mcpManagementService = mcpManagementService;
    }

    @PostMapping
    public void bindServers(@PathVariable("appKey") String appKey,
                            @RequestBody McpBindRequest request) {
        mcpManagementService.bindServers(appKey, request.mcpServerIds());
    }

    @GetMapping
    public List<Long> getBindings(@PathVariable("appKey") String appKey) {
        return mcpManagementService.getServerBindings(appKey);
    }
}
