package xin.v5ai.nb.infrastructure.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "v5ai.agentscope")
public class AgentScopeRuntimeProperties {
    private String workspace = ".agentscope/workspace";
    private String stateDirectory = ".agentscope/state";

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public String getStateDirectory() {
        return stateDirectory;
    }

    public void setStateDirectory(String stateDirectory) {
        this.stateDirectory = stateDirectory;
    }
}
