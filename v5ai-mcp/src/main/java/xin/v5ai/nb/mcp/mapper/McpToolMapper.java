package xin.v5ai.nb.mcp.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.mcp.domain.McpTool;
import xin.v5ai.nb.mcp.domain.vo.McpToolVo;

import java.util.List;

/**
 * <p>
 * MCP Tool Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface McpToolMapper extends BaseMapperPlus<McpTool, McpToolVo> {

    /**
     * 删除某 Server 下不在保留名单内的 Tool（用于 Tool 发现后的增量清理）。
     *
     * @param serverId       Server ID
     * @param keepToolNames  保留的工具名
     */
    default void deleteByServerIdNotIn(Long serverId, List<String> keepToolNames) {
        delete(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getServerId, serverId)
                .notIn(!keepToolNames.isEmpty(), McpTool::getToolName, keepToolNames));
    }
}
