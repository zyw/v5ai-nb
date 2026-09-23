package xin.v5ai.nb.mcp.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.mcp.service.IMcpServerService;

import java.util.List;

/**
 * AgentDTO 绑定 MCP Server API。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agents/{agentKey}/mcp-bindings")
public class AgentMcpBindingController {

    private final IMcpServerService mcpServerService;

    @SaCheckPermission("agent:agent:edit")
    @PostMapping
    public R<Void> bindServers(@PathVariable("agentKey") String agentKey,
                               @RequestBody McpBindRequest request) {
        if (request == null || request.mcpServerIds() == null) {
            throw new IllegalArgumentException("mcpServerIds is required");
        }
        mcpServerService.bindServers(agentKey, request.mcpServerIds());
        return R.ok();
    }

    @SaCheckPermission("agent:agent:query")
    @GetMapping
    public R<List<Long>> getBindings(@PathVariable("agentKey") String agentKey) {
        return R.ok(mcpServerService.getServerBindings(agentKey));
    }
}
