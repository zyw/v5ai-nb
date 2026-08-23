package xin.v5ai.nb.mcp.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.agentscope.core.domain.dto.McpToolDTO;
import xin.v5ai.nb.common.agentscope.core.service.McpToolService;
import xin.v5ai.nb.mcp.domain.McpTool;

import java.util.List;

/**
 * {@link McpToolService} 的 MyBatis-Plus 实现（运行时按 Server 读取已缓存 Tool，权限过滤在调用方）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
@RequiredArgsConstructor
public class MyBatisMcpToolRepository implements McpToolService {

    private final McpToolMapper toolMapper;

    @Override
    public List<McpToolDTO> selectList(Long serverId) {
        return toolMapper.selectList(new LambdaQueryWrapper<McpTool>()
                        .eq(McpTool::getServerId, serverId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private McpToolDTO toDomain(McpTool entity) {
        var dto = new McpToolDTO();
        dto.setId(entity.getId());
        dto.setServerId(entity.getServerId());
        dto.setToolName(entity.getToolName());
        dto.setDescription(entity.getDescription());
        dto.setInputSchemaJson(entity.getInputSchemaJson());
        dto.setReadOnly(entity.getReadOnly());
        dto.setPermission(entity.getPermission());
        dto.setLastDiscoveredAt(entity.getLastDiscoveredAt());
        return dto;
    }
}
