package xin.v5ai.nb.mcp.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentMcpBindingDTO;
import xin.v5ai.nb.common.agentscope.core.service.AgentMcpBindingService;
import xin.v5ai.nb.mcp.domain.AgentMcpBinding;

import java.util.List;

/**
 * {@link AgentMcpBindingService} 的 MyBatis-Plus 实现（AgentDTO-MCP 绑定读写，供级联删除与运行时解析）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
@RequiredArgsConstructor
public class MyBatisAgentMcpBindingRepository implements AgentMcpBindingService {

    private final AgentMcpBindingMapper bindingMapper;

    @Override
    public void delete(String agentKey) {
        bindingMapper.delete(new LambdaQueryWrapper<AgentMcpBinding>()
                .eq(AgentMcpBinding::getAgentKey, agentKey));
    }

    @Override
    public List<AgentMcpBindingDTO> selectList(String agentKey) {
        return bindingMapper.selectList(new LambdaQueryWrapper<AgentMcpBinding>()
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
