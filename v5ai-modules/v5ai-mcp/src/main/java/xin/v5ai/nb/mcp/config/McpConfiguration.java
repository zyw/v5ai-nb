package xin.v5ai.nb.mcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.mcp.config.properties.McpRuntimeProperties;

@Configuration
@EnableConfigurationProperties(McpRuntimeProperties.class)
public class McpConfiguration {
}
