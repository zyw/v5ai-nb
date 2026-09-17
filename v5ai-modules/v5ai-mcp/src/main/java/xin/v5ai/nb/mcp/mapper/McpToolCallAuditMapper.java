package xin.v5ai.nb.mcp.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.mcp.domain.McpToolCallAudit;
import xin.v5ai.nb.mcp.domain.vo.McpToolCallAuditVo;

/**
 * <p>
 * MCP Tool 调用审计 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface McpToolCallAuditMapper extends BaseMapperPlus<McpToolCallAudit, McpToolCallAuditVo> {
}
