package xin.v5ai.nb.mcp.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.mcp.domain.McpServer;
import xin.v5ai.nb.mcp.domain.vo.McpServerVo;

/**
 * <p>
 * MCP Server Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface McpServerMapper extends BaseMapperPlus<McpServer, McpServerVo> {
}
