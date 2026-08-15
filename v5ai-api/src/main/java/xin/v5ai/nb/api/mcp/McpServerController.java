package xin.v5ai.nb.api.mcp;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.mcp.CreateMcpServerCommand;
import xin.v5ai.nb.mcp.McpManagementService;
import xin.v5ai.nb.mcp.UpdateMcpServerCommand;

import java.util.List;

/**
 * MCP Server 管理 API：注册、连接测试、Tool 发现、权限调整与调用审计。
 */
@RestController
@RequestMapping("/api/admin/mcp-servers")
public class McpServerController {
    private final McpManagementService mcpManagementService;

    public McpServerController(McpManagementService mcpManagementService) {
        this.mcpManagementService = mcpManagementService;
    }

    @PostMapping
    public McpServerResponse createServer(@RequestBody CreateMcpServerRequest request) {
        return McpServerResponse.from(mcpManagementService.createServer(new CreateMcpServerCommand(
                request.name(), request.transportType(), request.endpoint(), request.args(),
                request.headers(), request.envVars(), request.timeoutSeconds())));
    }

    @GetMapping
    public List<McpServerResponse> listServers() {
        return mcpManagementService.listServers().stream()
                .map(McpServerResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public McpServerResponse updateServer(@PathVariable("id") Long id,
                                          @RequestBody UpdateMcpServerRequest request) {
        return McpServerResponse.from(mcpManagementService.updateServer(new UpdateMcpServerCommand(
                id, request.name(), request.transportType(), request.endpoint(), request.args(),
                request.headers(), request.envVars(), request.timeoutSeconds())));
    }

    @DeleteMapping("/{id}")
    public void disableServer(@PathVariable("id") Long id) {
        mcpManagementService.disableServer(id);
    }

    @PostMapping("/{id}/test-connection")
    public McpConnectionTestResponse testConnection(@PathVariable("id") Long id) {
        return McpConnectionTestResponse.from(id, mcpManagementService.testConnection(id));
    }

    @PostMapping("/{id}/discover-tools")
    public List<McpToolResponse> discoverTools(@PathVariable("id") Long id) {
        return mcpManagementService.discoverTools(id).stream()
                .map(McpToolResponse::from)
                .toList();
    }

    @GetMapping("/{id}/tools")
    public List<McpToolResponse> listTools(@PathVariable("id") Long id) {
        return mcpManagementService.listTools(id).stream()
                .map(McpToolResponse::from)
                .toList();
    }

    @PutMapping("/{id}/tools/{toolName}/permission")
    public McpToolResponse updateToolPermission(@PathVariable("id") Long id,
                                                @PathVariable("toolName") String toolName,
                                                @RequestBody McpToolPermissionRequest request) {
        mcpManagementService.updateToolPermission(id, toolName, request.permission());
        return mcpManagementService.listTools(id).stream()
                .filter(tool -> tool.toolName().equals(toolName))
                .findFirst()
                .map(McpToolResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("MCP tool does not exist: " + toolName));
    }

    @GetMapping("/{id}/tool-calls")
    public List<McpToolCallAuditResponse> listToolCallsByServer(@PathVariable("id") Long id) {
        return mcpManagementService.listToolCallsByServer(id).stream()
                .map(McpToolCallAuditResponse::from)
                .toList();
    }
}
