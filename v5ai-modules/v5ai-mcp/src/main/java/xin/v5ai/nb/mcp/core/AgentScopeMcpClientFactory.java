package xin.v5ai.nb.mcp.core;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.McpConnection;
import xin.v5ai.nb.common.agentscope.utils.McpJsonCodec;
import xin.v5ai.nb.common.agentscope.core.StdioCommandPolicy;
import xin.v5ai.nb.common.agentscope.enums.McpTransportType;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;
import xin.v5ai.nb.mcp.core.domain.dto.McpServerDTO;

import java.time.Duration;
import java.util.Map;

/**
 * 基于 AgentScope {@link McpClientBuilder} 的连接工厂：
 * 支持 Streamable HTTP / SSE / Stdio 三种传输，Stdio 命令受 {@link StdioCommandPolicy} 约束。
 * headers/env 以密文落库，连接前在此解密还原。
 */
@Component
@RequiredArgsConstructor
public class AgentScopeMcpClientFactory implements McpClientFactory {

    private final StdioCommandPolicy stdioCommandPolicy;
    private final CredentialCipher cipher;

    @Override
    public McpConnection connect(McpServerDTO server) {
        var builder = McpClientBuilder.create(server.getName());
        var timeout = Duration.ofSeconds(server.getTimeoutSeconds() == null ? 30 : server.getTimeoutSeconds());
        switch (McpTransportType.valueOf(server.getTransportType())) {
            case STREAMABLE_HTTP -> {
                builder.streamableHttpTransport(server.getEndpoint());
                applyHeaders(builder, decryptMap(server.getHeadersCiphertext()));
            }
            case SSE -> {
                builder.sseTransport(server.getEndpoint());
                applyHeaders(builder, decryptMap(server.getHeadersCiphertext()));
            }
            case STDIO -> {
                stdioCommandPolicy.validate(server.getEndpoint());
                builder.stdioTransport(server.getEndpoint(),
                        McpJsonCodec.toStringList(server.getArgsJson()),
                        decryptMap(server.getEnvCiphertext()));
            }
            default -> throw new IllegalArgumentException("unsupported MCP transport: " + server.getTransportType());
        }
        builder.timeout(timeout)
                .initializationTimeout(Duration.ofSeconds(Math.max(5, Math.min(timeout.getSeconds(), 30))));
        McpClientWrapper wrapper = builder.buildAsync().blockOptional().orElseThrow(
                () -> new IllegalStateException("MCP client build returned empty for server: " + server.getName()));
        return new AgentScopeMcpConnection(wrapper);
    }

    private Map<String, String> decryptMap(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return Map.of();
        }
        return McpJsonCodec.toStringMap(cipher.decrypt(ciphertext));
    }

    private static void applyHeaders(McpClientBuilder builder, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            builder.headers(headers);
        }
    }
}
