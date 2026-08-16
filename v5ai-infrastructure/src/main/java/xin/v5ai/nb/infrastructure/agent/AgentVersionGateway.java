package xin.v5ai.nb.infrastructure.agent;

import java.util.List;

public interface AgentVersionGateway {
    void insertVersion(AgentVersionEntity entity);

    long countVersions(String agentKey);

    List<AgentVersionEntity> selectVersions(String agentKey);
}
