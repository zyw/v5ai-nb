package xin.v5ai.nb.agent;

import java.util.List;

public interface AgentRepository {
    Agent findByAgentKey(String agentKey);

    void save(Agent agent);

    void update(Agent agent);

    void disable(String agentKey);

    List<Agent> list();

    long nextVersion(String agentKey);

    void saveVersion(AgentVersion version);

    List<AgentVersion> listVersions(String agentKey);

    void publish(String agentKey, long version);
}
