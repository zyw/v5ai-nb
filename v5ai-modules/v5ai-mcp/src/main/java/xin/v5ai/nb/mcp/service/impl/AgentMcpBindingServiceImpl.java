package xin.v5ai.nb.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.mcp.core.AgentMcpBindingService;
import xin.v5ai.nb.mcp.core.domain.dto.AgentMcpBindingDTO;
import xin.v5ai.nb.mcp.domain.AgentMcpBinding;
import xin.v5ai.nb.mcp.mapper.AgentMcpBindingMapper;
import xin.v5ai.nb.mcp.service.IAgentMcpBindingService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentMcpBindingServiceImpl implements IAgentMcpBindingService, AgentMcpBindingService {

    private final AgentMcpBindingMapper baseMapper;

    @Override
    public void delete(String agentKey) {
        baseMapper.delete(new LambdaQueryWrapper<AgentMcpBinding>()
                .eq(AgentMcpBinding::getAgentKey, agentKey));
    }

    @Override
    public List<AgentMcpBindingDTO> selectList(String agentKey) {
        return baseMapper.selectList(new LambdaQueryWrapper<AgentMcpBinding>()
                        .eq(AgentMcpBinding::getAgentKey, agentKey))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private AgentMcpBindingDTO toDomain(AgentMcpBinding entity) {
        var dto = new AgentMcpBindingDTO();
        dto.setAgentKey(entity.getAgentKey());
        dto.setMcpServerId(entity.getMcpServerId());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
