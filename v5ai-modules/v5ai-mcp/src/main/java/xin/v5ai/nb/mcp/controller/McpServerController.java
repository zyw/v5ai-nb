package xin.v5ai.nb.mcp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.mcp.domain.bo.McpServerBo;
import xin.v5ai.nb.mcp.domain.vo.McpServerVo;
import xin.v5ai.nb.mcp.domain.vo.McpToolCallAuditVo;
import xin.v5ai.nb.mcp.domain.vo.McpToolVo;
import xin.v5ai.nb.mcp.service.IMcpServerService;

import java.util.List;

/**
 * MCP Server 管理 API：注册、连接测试、Tool 发现、权限调整与调用审计。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/mcp-servers")
public class McpServerController extends BaseController {

    private final IMcpServerService mcpServerService;

    @PostMapping
    @Log(title = "新建MCP服务", businessType = BusinessType.INSERT)
    public R<McpServerVo> createServer(@Validated(AddGroup.class) @RequestBody McpServerBo bo) {
        var created = mcpServerService.createServer(bo);
        return R.ok(created);
    }

    @GetMapping
    public R<PageResult<McpServerVo>> listServers(McpServerBo bo, PageQuery pageQuery) {
        return R.ok(mcpServerService.queryPageList(bo, pageQuery));
    }

    @GetMapping("/options")
    public R<List<OptionDTO>> listServerOptions() {
        return R.ok(mcpServerService.queryOptionList());
    }

    @PutMapping
    @Log(title = "更新MCP服务", businessType = BusinessType.UPDATE)
    public R<McpServerVo> updateServer(@Validated(EditGroup.class) @RequestBody McpServerBo bo) {
        return R.ok(mcpServerService.updateServer(bo));
    }

    @PutMapping("/{id}/disable")
    @Log(title = "禁用MCP服务", businessType = BusinessType.DISABLE)
    public R<Void> disableServer(@PathVariable("id") Long id) {
        return toAjax(mcpServerService.disableServer(id));
    }

    @PutMapping("/{id}/enable")
    @Log(title = "启用MCP服务", businessType = BusinessType.ENABLE)
    public R<Void> enableServer(@PathVariable("id") Long id) {
        return toAjax(mcpServerService.enableServer(id));
    }

    @PostMapping("/{id}/test-connection")
    public R<McpConnectionTestResponse> testConnection(@PathVariable("id") Long id) {
        var result = mcpServerService.testConnection(id);
        return R.ok(new McpConnectionTestResponse(id, result.ok(), result.message(), result.toolCount()));
    }

    @PostMapping("/{id}/discover-tools")
    public R<List<McpToolVo>> discoverTools(@PathVariable("id") Long id) {
        return R.ok(mcpServerService.discoverTools(id));
    }

    @GetMapping("/{id}/tools")
    public R<PageResult<McpToolVo>> listTools(@PathVariable("id") Long id, PageQuery pageQuery) {
        var list = mcpServerService.listTools(id);
        return R.ok(PageResult.build(list, list.size()));
    }

    @PutMapping("/{id}/tools/{toolName}/permission")
    public R<McpToolVo> updateToolPermission(@PathVariable("id") Long id,
                                             @PathVariable("toolName") String toolName,
                                             @RequestBody McpToolPermissionRequest request) {
        if (request == null || request.permission() == null) {
            throw new IllegalArgumentException("permission is required");
        }
        return R.ok(mcpServerService.updateToolPermission(id, toolName, request.permission()));
    }

    @GetMapping("/{id}/tool-calls")
    public R<PageResult<McpToolCallAuditVo>> listToolCallsByServer(@PathVariable("id") Long id,
                                                                   PageQuery pageQuery) {
        var list = mcpServerService.listToolCallsByServer(id);
        return R.ok(PageResult.build(list, (long) list.size()));
    }
}
