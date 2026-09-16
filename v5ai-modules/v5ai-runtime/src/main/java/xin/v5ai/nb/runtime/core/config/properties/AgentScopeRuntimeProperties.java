package xin.v5ai.nb.runtime.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "v5ai.agentscope")
public class AgentScopeRuntimeProperties {
    private String workspace = ".agentscope/workspace";
    private String stateDirectory = ".agentscope/state";
    /** 联网搜索（Tavily）API Key；为空则不启用联网搜索工具。 */
    private String tavilyApiKey;
}
