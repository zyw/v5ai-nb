package xin.v5ai.nb.infrastructure.mcp;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.mcp.McpServer;
import xin.v5ai.nb.mcp.McpServerRepository;
import xin.v5ai.nb.mcp.McpServerStatus;
import xin.v5ai.nb.mcp.McpTransportType;
import xin.v5ai.nb.model.CredentialCipher;

import java.util.List;

/**
 * MCP Server 持久化：headers/env 属敏感配置，以应用级加密密钥加密后落库（密文列）。
 */
@Repository
public class MyBatisMcpServerRepository implements McpServerRepository {
    private final McpServerGateway gateway;
    private final CredentialCipher cipher;

    public MyBatisMcpServerRepository(McpServerGateway gateway, CredentialCipher cipher) {
        this.gateway = gateway;
        this.cipher = cipher;
    }

    @Override
    public McpServer save(McpServer server) {
        var entity = new McpServerEntity();
        entity.setName(server.name());
        entity.setTransportType(server.transportType().name());
        entity.setEndpoint(server.endpoint());
        entity.setArgsJson(McpJsonCodec.toJson(server.args()));
        entity.setHeadersCiphertext(cipher.encrypt(McpJsonCodec.toJson(server.headers())));
        entity.setEnvCiphertext(cipher.encrypt(McpJsonCodec.toJson(server.envVars())));
        entity.setTimeoutSeconds(server.timeoutSeconds());
        entity.setStatus(server.status().name());
        entity.setLastTestStatus(server.lastTestStatus());
        entity.setLastTestMessage(server.lastTestMessage());
        entity.setLastTestedAt(server.lastTestedAt());
        gateway.insertServer(entity);
        return toDomain(entity);
    }

    @Override
    public McpServer update(McpServer server) {
        var entity = new McpServerEntity();
        entity.setId(server.id());
        entity.setName(server.name());
        entity.setTransportType(server.transportType().name());
        entity.setEndpoint(server.endpoint());
        entity.setArgsJson(McpJsonCodec.toJson(server.args()));
        entity.setHeadersCiphertext(cipher.encrypt(McpJsonCodec.toJson(server.headers())));
        entity.setEnvCiphertext(cipher.encrypt(McpJsonCodec.toJson(server.envVars())));
        entity.setTimeoutSeconds(server.timeoutSeconds());
        entity.setStatus(server.status().name());
        entity.setLastTestStatus(server.lastTestStatus());
        entity.setLastTestMessage(server.lastTestMessage());
        entity.setLastTestedAt(server.lastTestedAt());
        gateway.updateServer(entity);
        return server;
    }

    @Override
    public McpServer findById(Long id) {
        return toDomain(gateway.selectServerById(id));
    }

    @Override
    public List<McpServer> list() {
        return gateway.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void disable(Long id) {
        gateway.disableServer(id);
    }

    private McpServer toDomain(McpServerEntity entity) {
        if (entity == null) {
            return null;
        }
        return new McpServer(
                entity.getId(),
                entity.getName(),
                McpTransportType.valueOf(entity.getTransportType()),
                entity.getEndpoint(),
                McpJsonCodec.toStringList(entity.getArgsJson()),
                McpJsonCodec.toStringMap(cipher.decrypt(entity.getHeadersCiphertext())),
                McpJsonCodec.toStringMap(cipher.decrypt(entity.getEnvCiphertext())),
                entity.getTimeoutSeconds(),
                McpServerStatus.valueOf(entity.getStatus()),
                entity.getLastTestStatus(),
                entity.getLastTestMessage(),
                entity.getLastTestedAt());
    }
}
