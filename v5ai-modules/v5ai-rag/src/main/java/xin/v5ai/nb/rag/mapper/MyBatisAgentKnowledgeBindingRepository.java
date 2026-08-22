package xin.v5ai.nb.rag.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.AgentKnowledgeBindingRepository;
import xin.v5ai.nb.rag.domain.AgentKnowledgeBinding;

import java.util.List;

/**
 * {@link AgentKnowledgeBindingRepository} 的 MyBatis-Plus 实现（检索上下文消费）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
public class MyBatisAgentKnowledgeBindingRepository implements AgentKnowledgeBindingRepository {

    private final AgentKnowledgeBindingMapper bindingMapper;

    public MyBatisAgentKnowledgeBindingRepository(AgentKnowledgeBindingMapper bindingMapper) {
        this.bindingMapper = bindingMapper;
    }

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
