package xin.v5ai.nb.mcp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.mcp.domain.vo.McpToolCallAuditVo;
import xin.v5ai.nb.mcp.service.IMcpServerService;

import java.util.List;

/**
 * MCP Tool 调用审计查询 API：支持按 runId 或 serverId 检索。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/mcp-tool-calls")
public class McpToolCallController {

    private final IMcpServerService mcpServerService;

    @GetMapping
    public R<PageResult<McpToolCallAuditVo>> listToolCalls(
            PageQuery pageQuery,
            @RequestParam(value = "runId", required = false) String runId,
            @RequestParam(value = "serverId", required = false) Long serverId
    ) {
        if (runId != null && !runId.isBlank()) {
            List<McpToolCallAuditVo> list = mcpServerService.listToolCallsByRun(runId);
            return R.ok(PageResult.build(list, (long) list.size()));
        }
        if (serverId != null) {
            List<McpToolCallAuditVo> list = mcpServerService.listToolCallsByServer(serverId);
            return R.ok(PageResult.build(list, (long) list.size()));
        }
        return R.ok(PageResult.build(List.of(), 0L));
    }
}
