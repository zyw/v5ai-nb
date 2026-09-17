package xin.v5ai.nb.mcp.core.service;

import xin.v5ai.nb.mcp.core.domain.dto.McpToolDTO;

import java.util.List;

/**
 * MCP Tool 仓储端口：运行时按 MCP Server 读取已发现的 Tool 缓存。
 *
 * <p>数据来自“发现工具”后落库的缓存，不在此处发起远端发现；本接口不做权限过滤，
 * 调用方（如 {@code AgentMcpToolResolver}）自行剔除 DENY 工具。查询的顺序不保证。</p>
 */
public interface McpToolService {

    /**
     * 查询某个 MCP Server 下已缓存的全部 Tool。
     *
     * @param serverId MCP Server 主键
     * @return Tool 列表；该 Server 尚未发现工具时返回空列表
     */
    List<McpToolDTO> selectList(Long serverId);
}