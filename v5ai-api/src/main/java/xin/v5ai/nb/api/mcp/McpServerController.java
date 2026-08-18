package xin.v5ai.nb.api.mcp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
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

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    public McpServerController(McpManagementService mcpManagementService) {
        this.mcpManagementService = mcpManagementService;
    }

    @PostMapping
    public R<McpServerResponse> createServer(@RequestBody CreateMcpServerRequest request) {
        var created = McpServerResponse.from(mcpManagementService.createServer(new CreateMcpServerCommand(
                request.name(), request.transportType(), request.endpoint(), request.args(),
                request.headers(), request.envVars(), request.timeoutSeconds())));
        audit("CREATE", "mcp-server", String.valueOf(created.id()), "create mcp server " + created.name());
        return R.ok(created);
    }

    @GetMapping
    public R<PageResult<McpServerResponse>> listServers(PageQuery pageQuery) {
        var list = mcpManagementService.listServers().stream()
                .map(McpServerResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @PutMapping("/{id}")
    public R<McpServerResponse> updateServer(@PathVariable("id") Long id,
                                          @RequestBody UpdateMcpServerRequest request) {
        return R.ok(McpServerResponse.from(mcpManagementService.updateServer(new UpdateMcpServerCommand(
                id, request.name(), request.transportType(), request.endpoint(), request.args(),
                request.headers(), request.envVars(), request.timeoutSeconds()))));
    }

    @DeleteMapping("/{id}")
    public R<Void> disableServer(@PathVariable("id") Long id) {
        mcpManagementService.disableServer(id);
        audit("DISABLE", "mcp-server", String.valueOf(id), "disable mcp server");
        return R.ok();
    }

    @PostMapping("/{id}/test-connection")
    public R<McpConnectionTestResponse> testConnection(@PathVariable("id") Long id) {
        return R.ok(McpConnectionTestResponse.from(id, mcpManagementService.testConnection(id)));
    }

    @PostMapping("/{id}/discover-tools")
    public R<List<McpToolResponse>> discoverTools(@PathVariable("id") Long id) {
        var list = mcpManagementService.discoverTools(id).stream()
                .map(McpToolResponse::from)
                .toList();
        return R.ok(list);
    }

    @GetMapping("/{id}/tools")
    public R<PageResult<McpToolResponse>> listTools(@PathVariable("id") Long id, PageQuery pageQuery) {
        var list = mcpManagementService.listTools(id).stream()
                .map(McpToolResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @PutMapping("/{id}/tools/{toolName}/permission")
    public R<McpToolResponse> updateToolPermission(@PathVariable("id") Long id,
                                                @PathVariable("toolName") String toolName,
                                                @RequestBody McpToolPermissionRequest request) {
        mcpManagementService.updateToolPermission(id, toolName, request.permission());
        return R.ok(mcpManagementService.listTools(id).stream()
                .filter(tool -> tool.toolName().equals(toolName))
                .findFirst()
                .map(McpToolResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("MCP tool does not exist: " + toolName)));
    }

    @GetMapping("/{id}/tool-calls")
    public R<PageResult<McpToolCallAuditResponse>> listToolCallsByServer(@PathVariable("id") Long id,
                                                                         PageQuery pageQuery) {
        var list = mcpManagementService.listToolCallsByServer(id).stream()
                .map(McpToolCallAuditResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        if (auditRecorder == null) {
            return;
        }
        String actor;
        try {
            actor = LoginHelper.getUsername();
        } catch (Exception exception) {
            actor = null;
        }
        auditRecorder.record(null, actor, action, targetType, targetId, detail, null);
    }
}
