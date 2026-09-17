package xin.v5ai.nb.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.mcp.core.domain.dto.McpToolDTO;
import xin.v5ai.nb.mcp.core.service.McpToolService;
import xin.v5ai.nb.mcp.domain.McpTool;
import xin.v5ai.nb.mcp.mapper.McpToolMapper;
import xin.v5ai.nb.mcp.service.IMcpToolService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class McpToolServiceImpl implements IMcpToolService, McpToolService {

    private final McpToolMapper baseMapper;

    @Override
    public List<McpToolDTO> selectList(Long serverId) {
        return baseMapper.selectList(new LambdaQueryWrapper<McpTool>()
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
