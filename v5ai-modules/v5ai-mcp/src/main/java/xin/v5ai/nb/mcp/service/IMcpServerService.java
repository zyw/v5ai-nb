package xin.v5ai.nb.mcp.service;

import xin.v5ai.nb.common.core.domain.OptionDTO;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.mcp.McpConnectionTestResult;
import xin.v5ai.nb.mcp.McpToolPermission;
import xin.v5ai.nb.mcp.domain.bo.McpServerBo;
import xin.v5ai.nb.mcp.domain.vo.McpServerVo;
import xin.v5ai.nb.mcp.domain.vo.McpToolCallAuditVo;
import xin.v5ai.nb.mcp.domain.vo.McpToolVo;

import java.util.List;

/**
 * MCP Server 管理服务：Server 增删改查、连接测试、Tool 发现、Agent 绑定、
 * 权限调整与调用审计查询。本模块不依赖任何具体 MCP 传输库，
 * 连接能力通过 {@link xin.v5ai.nb.mcp.McpClientFactory} 注入（基础设施层实现）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
public interface IMcpServerService {

    /**
     * 分页查询 MCP Server。
     */
    PageResult<McpServerVo> queryPageList(McpServerBo bo, PageQuery pageQuery);

    /**
     * 查询 MCP Server 列表。
     */
    List<McpServerVo> queryList(McpServerBo bo);

    /**
     * 可绑定的 MCP Server 下拉选项（仅 ACTIVE）。
     */
    List<OptionDTO> queryOptionList();

    /**
     * 创建 MCP Server。
     */
    McpServerVo createServer(McpServerBo bo);

    /**
     * 更新 MCP Server（null 字段保留原值）。
     */
    McpServerVo updateServer(McpServerBo bo);

    /**
     * 禁用 MCP Server。
     */
    void disableServer(Long id);

    /**
     * 查询单个 MCP Server。
     */
    McpServerVo getServer(Long id);

    /**
     * 连接测试：记录最近一次测试结果。
     */
    McpConnectionTestResult testConnection(Long serverId);

    /**
     * Tool 发现：连接后全量 upsert 工具元数据并清理已消失的工具。
     */
    List<McpToolVo> discoverTools(Long serverId);

    /**
     * 查询某 Server 已缓存的 Tool 列表。
     */
    List<McpToolVo> listTools(Long serverId);

    /**
     * 调整 Tool 权限。
     */
    McpToolVo updateToolPermission(Long serverId, String toolName, McpToolPermission permission);

    /**
     * 全量替换某 Agent 绑定的 MCP Server 集合。
     */
    void bindServers(String agentKey, List<Long> mcpServerIds);

    /**
     * 查询某 Agent 绑定的 MCP Server ID 集合。
     */
    List<Long> getServerBindings(String agentKey);

    /**
     * 按运行 ID 查询 Tool 调用审计。
     */
    List<McpToolCallAuditVo> listToolCallsByRun(String runId);

    /**
     * 按 Server ID 查询 Tool 调用审计。
     */
    List<McpToolCallAuditVo> listToolCallsByServer(Long serverId);
}
