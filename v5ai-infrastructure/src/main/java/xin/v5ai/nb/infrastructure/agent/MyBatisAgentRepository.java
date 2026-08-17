package xin.v5ai.nb.infrastructure.agent;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.AgentRepository;
import xin.v5ai.nb.agent.AgentStatus;
import xin.v5ai.nb.agent.AgentVersion;

import java.util.List;

@Primary
@Repository
public class MyBatisAgentRepository implements AgentRepository {
    private final AgentGateway agentGateway;
    private final AgentVersionGateway versionGateway;

    public MyBatisAgentRepository(AgentGateway agentGateway, AgentVersionGateway versionGateway) {
        this.agentGateway = agentGateway;
        this.versionGateway = versionGateway;
    }

    @Override
    public Agent findByAgentKey(String agentKey) {
        return toDomain(agentGateway.selectByAgentKey(agentKey));
    }

    @Override
    public void save(Agent application) {
        agentGateway.insertAgent(toEntity(application));
    }

    @Override
    public void update(Agent application) {
        var entity = toEntity(application);
        entity.setId(agentGateway.selectByAgentKey(application.agentKey()).getId());
        agentGateway.updateAgent(entity);
    }

    @Override
    public void disable(String agentKey) {
        agentGateway.disable(agentKey);
    }

    @Override
    public List<Agent> list() {
        return agentGateway.selectAgents().stream()
                .map(MyBatisAgentRepository::toDomain)
                .toList();
    }

    @Override
    public long nextVersion(String agentKey) {
        return versionGateway.countVersions(agentKey) + 1;
    }

    @Override
    public void saveVersion(AgentVersion version) {
        var entity = new AgentVersionEntity();
        entity.setAgentKey(version.agentKey());
        entity.setVersion(version.version());
        entity.setSnapshotJson(version.snapshotJson());
        entity.setDescription(version.description());
        versionGateway.insertVersion(entity);
    }

    @Override
    public List<AgentVersion> listVersions(String agentKey) {
        return versionGateway.selectVersions(agentKey).stream()
                .map(entity -> new AgentVersion(entity.getAgentKey(), entity.getVersion(),
                        entity.getSnapshotJson(), entity.getDescription()))
                .toList();
    }

    @Override
    public void publish(String agentKey, long version) {
        agentGateway.updatePublished(agentKey, AgentStatus.PUBLISHED.name(), version);
    }

    private static AgentEntity toEntity(Agent application) {
        var entity = new AgentEntity();
        entity.setAgentKey(application.agentKey());
        entity.setName(application.name());
        entity.setDescription(application.description());
        entity.setStatus(application.status().name());
        entity.setModelId(application.modelId());
        entity.setPublishedVersion(application.publishedVersion());
        entity.setSystemPrompt(application.systemPrompt());
        entity.setAvatar(application.avatar());
        entity.setGreeting(application.greeting());
        entity.setPresetQuestions(application.presetQuestions());
        entity.setMemoryEnabled(application.memoryEnabled());
        entity.setMcpEnabled(application.mcpEnabled());
        entity.setSkillEnabled(application.skillEnabled());
        entity.setWebSearchEnabled(application.webSearchEnabled());
        entity.setRagEnabled(application.ragEnabled());
        return entity;
    }

    private static Agent toDomain(AgentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Agent(entity.getAgentKey(), entity.getName(), entity.getDescription(),
                AgentStatus.valueOf(entity.getStatus()),
                entity.getModelId(), entity.getPublishedVersion(), entity.getSystemPrompt(),
                entity.getAvatar(), entity.getGreeting(), entity.getPresetQuestions(),
                Boolean.TRUE.equals(entity.getMemoryEnabled()),
                Boolean.TRUE.equals(entity.getMcpEnabled()),
                Boolean.TRUE.equals(entity.getSkillEnabled()),
                Boolean.TRUE.equals(entity.getWebSearchEnabled()),
                Boolean.TRUE.equals(entity.getRagEnabled()));
    }
}
