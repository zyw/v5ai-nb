package xin.v5ai.nb.agent.service.impl;

import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentVersionDTO;
import xin.v5ai.nb.common.agentscope.core.service.AgentService;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryAgentServiceImpl implements AgentService {
    /**
     * Agent缓存
     */
    private final Map<String, AgentDTO> agents = new ConcurrentHashMap<>();
    /**
     * Agent版本缓存
     */
    private final List<AgentVersionDTO> versions = new ArrayList<>();

    /**
     * 根据AgentKey查询Agent
     * @param agentKey AgentKey
     * @return AgentDTO
     */
    @Override
    public AgentDTO findByAgentKey(String agentKey) {
        return agents.get(agentKey);
    }

    /**
     * 保存Agent
     * @param application AgentDTO
     */
    @Override
    public void save(AgentDTO application) {
        agents.put(application.agentKey(), application);
    }

    /**
     * 更新Agent
     * @param application AgentDTO
     */
    @Override
    public void update(AgentDTO application) {
        agents.put(application.agentKey(), application);
    }

    /**
     * 删除Agent
     * @param agentKey AgentKey
     */
    @Override

    public void delete(String agentKey) {

        agents.remove(agentKey);

    }

    /**
     * 禁用Agent
     * @param agentKey AgentKey
     */

    @Override
    public void disable(String agentKey) {
        var current = agents.get(agentKey);
        if (current != null) {
            agents.put(agentKey, new AgentDTO(current.agentKey(), current.name(), current.description(),
                    AgentStatus.DISABLED, current.modelId(), current.publishedVersion()));
        }
    }

    /**
     * 列出所有Agent
     * @return Agent列表
     */
    @Override
    public List<AgentDTO> list() {
        return new ArrayList<>(agents.values());
    }
    /**
     * 获取Agent的下一个版本号
     * @param agentKey AgentKey
     * @return 下一个版本号
     */

    @Override
    public long nextVersion(String agentKey) {
        return versions.stream().filter(version -> version.agentKey().equals(agentKey)).count() + 1;
    }
    /**
     * 保存Agent版本
     * @param version Agent版本
     */

    @Override
    public void saveVersion(AgentVersionDTO version) {
        versions.add(version);
        /**
         * 列出Agent的所有版本
         * @param agentKey AgentKey
         * @return Agent版本列表
         */
    }

    @Override
    public List<AgentVersionDTO> listVersions(String agentKey) {
        return versions.stream().filter(version -> version.agentKey().equals(agentKey)).toList();
    }

    /**
     * 发布Agent
     * @param agentKey AgentKey
     * @param version 版本号
     */
    @Override
    public void publish(String agentKey, long version) {
        var current = agents.get(agentKey);
        agents.put(agentKey, new AgentDTO(current.agentKey(), current.name(), current.description(),
                AgentStatus.PUBLISHED, current.modelId(), version));
    }
}
