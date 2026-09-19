package xin.v5ai.nb.runtime.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import xin.v5ai.nb.common.agentscope.core.token.PromptTokenEstimator;

@Data
@ConfigurationProperties(prefix = "v5ai.agentscope")
public class AgentScopeRuntimeProperties {
    private String workspace = ".agentscope/workspace";
    private String stateDirectory = ".agentscope/state";
    /** 联网搜索（Tavily）API Key；为空则不启用联网搜索工具。 */
    private String tavilyApiKey;
    /**
     * 用量估算里每张图片折算的 token 数（{@code v5ai.agentscope.image-tokens-per-image}）。
     * 只影响「模型没有回报真实用量」时的兜底口径，不影响真实记账。
     */
    private long imageTokensPerImage = PromptTokenEstimator.DEFAULT_IMAGE_TOKENS;
    /**
     * 是否把模型的思考过程随助手消息落库（{@code v5ai.agentscope.reasoning-persist-enabled}）。
     *
     * <p>关闭后思考照常流式推送给客户端，只是刷新/重进会话就看不到了；给需要规避敏感内容
     * 或控制存储增长的部署留的开关。思考**从不回放给模型**，与这个开关无关。</p>
     */
    private boolean reasoningPersistEnabled = true;
}
