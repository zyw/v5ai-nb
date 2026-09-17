package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.dto.McpToolCallAuditDTO;

import java.util.List;

/**
 * MCP Tool 调用审计仓储端口：运行时每次 MCP 工具调用（成功/失败/拒绝）都写入一条审计记录，
 * 供追溯与合规查询；管理端按 run / server 维度读取。
 *
 * <p>实现对接数据库表（如 {@code v5ai_mcp_tool_call}）；本接口只声明契约，不关心存储方式。
 * 运行时调用方（如 {@code PlatformMcpTool}）在无可用实现时需容忍该端口为空。</p>
 */
public interface McpToolCallAuditService {

    /**
     * 新增一条审计记录。
     *
     * <p>{@code audit} 的 {@code id} 与 {@code createdAt} 由实现决定：{@code id} 忽略并由存储生成，
     * {@code createdAt} 为空时补当前时间。</p>
     *
     * @param audit 待落库的审计记录（必填字段：runId / toolName / decision / status）
     * @return 落库后的审计记录，含生成的 id 与最终 createdAt
     */
    McpToolCallAuditDTO save(McpToolCallAuditDTO audit);

    /**
     * 按运行 ID 查询该次运行内的全部工具调用审计记录，按记录主键倒序（最新在前）。
     *
     * @param runId 运行 ID
     * @return 审计记录列表；无记录时返回空列表
     */
    List<McpToolCallAuditDTO> findByRunId(String runId);

    /**
     * 按 MCP Server 查询其全部工具调用审计记录，按记录主键倒序（最新在前）。
     *
     * @param serverId MCP Server 主键
     * @return 审计记录列表；无记录时返回空列表
     */
    List<McpToolCallAuditDTO> findByServerId(Long serverId);
}
