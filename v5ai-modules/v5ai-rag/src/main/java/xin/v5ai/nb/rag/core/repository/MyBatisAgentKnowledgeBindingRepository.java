package xin.v5ai.nb.rag.core.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.domain.AgentKnowledgeBinding;
import xin.v5ai.nb.rag.mapper.AgentKnowledgeBindingMapper;

import java.util.List;

/**
 * {@link AgentKnowledgeBindingRepository} 的 MyBatis-Plus 实现（检索上下文消费）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
@RequiredArgsConstructor
public class MyBatisAgentKnowledgeBindingRepository implements AgentKnowledgeBindingRepository {

    private final AgentKnowledgeBindingMapper bindingMapper;

    @Override
    public void save(String agentKey, List<Long> knowledgeBaseIds) {
        bindingMapper.delete(new LambdaQueryWrapper<AgentKnowledgeBinding>()
                .eq(AgentKnowledgeBinding::getAgentKey, agentKey));
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            var binding = new AgentKnowledgeBinding();
            binding.setAgentKey(agentKey);
            binding.setKnowledgeBaseId(knowledgeBaseId);
            bindingMapper.insert(binding);
        }
    }

    @Override
    public List<Long> findByAgentKey(String agentKey) {
        return bindingMapper.selectList(new LambdaQueryWrapper<AgentKnowledgeBinding>()
                        .eq(AgentKnowledgeBinding::getAgentKey, agentKey))
                .stream()
                .map(AgentKnowledgeBinding::getKnowledgeBaseId)
                .toList();
    }

    @Override
    public void delete(String agentKey) {
        bindingMapper.delete(new LambdaQueryWrapper<AgentKnowledgeBinding>()
                .eq(AgentKnowledgeBinding::getAgentKey, agentKey));
    }
}
