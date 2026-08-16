package xin.v5ai.nb.api.agent;

import xin.v5ai.nb.agent.AgentVersion;

public record AgentVersionResponse(String agentKey, long version, String snapshotJson, String description) {
    static AgentVersionResponse from(AgentVersion version) {
        return new AgentVersionResponse(version.agentKey(), version.version(),
                version.snapshotJson(), version.description());
    }
}
