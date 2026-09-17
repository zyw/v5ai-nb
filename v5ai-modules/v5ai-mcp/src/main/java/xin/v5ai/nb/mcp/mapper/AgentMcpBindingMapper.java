package xin.v5ai.nb.mcp.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.mcp.domain.AgentMcpBinding;
import xin.v5ai.nb.mcp.domain.vo.AgentMcpBindingVo;

/**
 * <p>
 * AgentDTO-MCP Server 绑定 Mapper 接口（复合主键）。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface AgentMcpBindingMapper extends BaseMapperPlus<AgentMcpBinding, AgentMcpBindingVo> {
}
