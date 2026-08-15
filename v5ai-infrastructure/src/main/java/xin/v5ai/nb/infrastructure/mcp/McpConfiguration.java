package xin.v5ai.nb.infrastructure.mcp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(McpRuntimeProperties.class)
public class McpConfiguration {
}
