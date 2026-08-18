package xin.v5ai.nb.api.mcp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.mcp.McpManagementService;

import java.util.List;

/**
 * MCP Tool 调用审计查询 API：支持按 runId 或 serverId 检索。
 */
@RestController
@RequestMapping("/api/admin/mcp-tool-calls")
public class McpToolCallController {
    private final McpManagementService mcpManagementService;

    public McpToolCallController(McpManagementService mcpManagementService) {
        this.mcpManagementService = mcpManagementService;
    }

    @GetMapping
    public R<PageResult<McpToolCallAuditResponse>> listToolCalls(
            PageQuery pageQuery,
            @RequestParam(value = "runId", required = false) String runId,
            @RequestParam(value = "serverId", required = false) Long serverId
    ) {
        if (runId != null && !runId.isBlank()) {
            var list = mcpManagementService.listToolCallsByRun(runId).stream()
                    .map(McpToolCallAuditResponse::from)
                    .toList();
            return R.ok(PageUtils.page(list, pageQuery));
        }
        if (serverId != null) {
            var list = mcpManagementService.listToolCallsByServer(serverId).stream()
                    .map(McpToolCallAuditResponse::from)
                    .toList();
            return R.ok(PageUtils.page(list, pageQuery));
        }
        return R.ok(PageUtils.page(List.of(), pageQuery));
    }
}
