package xin.v5ai.nb.runtime.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "v5ai.agentscope")
public class AgentScopeRuntimeProperties {
    private String workspace = ".agentscope/workspace";
    private String stateDirectory = ".agentscope/state";
}
