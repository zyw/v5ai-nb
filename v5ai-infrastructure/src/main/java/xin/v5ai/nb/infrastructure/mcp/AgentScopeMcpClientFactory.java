package xin.v5ai.nb.infrastructure.mcp;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.mcp.McpClientFactory;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpServer;
import xin.v5ai.nb.mcp.McpTransportType;
import xin.v5ai.nb.mcp.StdioCommandPolicy;

import java.time.Duration;
import java.util.Map;

/**
 * 基于 AgentScope {@link McpClientBuilder} 的连接工厂：
 * 支持 Streamable HTTP / SSE / Stdio 三种传输，Stdio 命令受 {@link StdioCommandPolicy} 约束。
 */
@Component
public class AgentScopeMcpClientFactory implements McpClientFactory {
    private final StdioCommandPolicy stdioCommandPolicy;

    public AgentScopeMcpClientFactory(StdioCommandPolicy stdioCommandPolicy) {
        this.stdioCommandPolicy = stdioCommandPolicy;
    }

    @Override
    public McpConnection connect(McpServer server) {
        var builder = McpClientBuilder.create(server.name());
        var timeout = Duration.ofSeconds(server.timeoutSeconds() == null ? 30 : server.timeoutSeconds());
        switch (server.transportType()) {
            case STREAMABLE_HTTP -> {
                builder.streamableHttpTransport(server.endpoint());
                applyHeaders(builder, server.headers());
            }
            case SSE -> {
                builder.sseTransport(server.endpoint());
                applyHeaders(builder, server.headers());
            }
            case STDIO -> {
                stdioCommandPolicy.validate(server.endpoint());
                builder.stdioTransport(server.endpoint(), server.args(), server.envVars());
            }
            default -> throw new IllegalArgumentException("unsupported MCP transport: " + server.transportType());
        }
        builder.timeout(timeout)
                .initializationTimeout(Duration.ofSeconds(Math.max(5, Math.min(timeout.getSeconds(), 30))));
        McpClientWrapper wrapper = builder.buildAsync().blockOptional().orElseThrow(
                () -> new IllegalStateException("MCP client build returned empty for server: " + server.name()));
        return new AgentScopeMcpConnection(wrapper);
    }

    private static void applyHeaders(McpClientBuilder builder, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            builder.headers(headers);
        }
    }
}
