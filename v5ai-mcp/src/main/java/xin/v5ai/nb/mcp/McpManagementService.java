package xin.v5ai.nb.mcp;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * MCP 管理服务：Server 增删改查、连接测试、Tool 发现、Application 绑定、
 * 权限调整与调用审计查询。本模块不依赖任何具体 MCP 传输库，
 * 连接能力通过 {@link McpClientFactory} 注入（基础设施层实现）。
 */
@Service
public class McpManagementService {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final McpServerRepository serverRepository;
    private final McpToolRepository toolRepository;
    private final ApplicationMcpBindingRepository bindingRepository;
    private final McpToolCallAuditRepository auditRepository;
    private final McpClientFactory clientFactory;
    private final StdioCommandPolicy stdioCommandPolicy;

    public McpManagementService(
            McpServerRepository serverRepository,
            McpToolRepository toolRepository,
            ApplicationMcpBindingRepository bindingRepository,
            McpToolCallAuditRepository auditRepository,
            McpClientFactory clientFactory,
            StdioCommandPolicy stdioCommandPolicy
    ) {
        this.serverRepository = serverRepository;
        this.toolRepository = toolRepository;
        this.bindingRepository = bindingRepository;
        this.auditRepository = auditRepository;
        this.clientFactory = clientFactory;
        this.stdioCommandPolicy = stdioCommandPolicy;
    }

    // ---------- Server 管理 ----------

    public McpServer createServer(CreateMcpServerCommand command) {
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("MCP server name is required");
        }
        if (command.transportType() == null) {
            throw new IllegalArgumentException("transport type is required");
        }
        validateEndpoint(command.transportType(), command.endpoint());
        var server = new McpServer(null, command.name().trim(), command.transportType(),
                command.endpoint() == null ? null : command.endpoint().trim(),
                command.args(), command.headers(), command.envVars(),
                command.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : command.timeoutSeconds(),
                McpServerStatus.ACTIVE, null, null, null);
        return serverRepository.save(server);
    }

    public McpServer updateServer(UpdateMcpServerCommand command) {
        if (command.id() == null) {
            throw new IllegalArgumentException("server id is required");
        }
        var existing = requireServer(command.id());
        var transportType = command.transportType() == null ? existing.transportType() : command.transportType();
        var endpoint = command.endpoint() == null ? existing.endpoint() : command.endpoint().trim();
        if (command.endpoint() != null) {
            validateEndpoint(transportType, endpoint);
        }
        var server = new McpServer(
                existing.id(),
                command.name() == null || command.name().isBlank() ? existing.name() : command.name().trim(),
                transportType,
                endpoint,
                command.args() == null ? existing.args() : command.args(),
                command.headers() == null ? existing.headers() : command.headers(),
                command.envVars() == null ? existing.envVars() : command.envVars(),
                command.timeoutSeconds() == null ? existing.timeoutSeconds() : command.timeoutSeconds(),
                existing.status(),
                existing.lastTestStatus(),
                existing.lastTestMessage(),
                existing.lastTestedAt());
        return serverRepository.update(server);
    }

    public void disableServer(Long id) {
        requireServer(id);
        serverRepository.disable(id);
    }

    public McpServer getServer(Long id) {
        return requireServer(id);
    }

    public List<McpServer> listServers() {
        return serverRepository.list();
    }

    // ---------- Application 绑定 ----------

    public void bindServers(String appKey, List<Long> mcpServerIds) {
        if (appKey == null || appKey.isBlank()) {
            throw new IllegalArgumentException("appKey is required");
        }
        if (mcpServerIds == null) {
            throw new IllegalArgumentException("mcpServerIds is required");
        }
        for (Long serverId : mcpServerIds) {
            requireActiveServer(serverId);
        }
        bindingRepository.save(appKey, mcpServerIds);
    }

    public List<Long> getServerBindings(String appKey) {
        return bindingRepository.findByAppKey(appKey);
    }

    // ---------- 连接测试 ----------

    public McpConnectionTestResult testConnection(Long serverId) {
        var server = requireServer(serverId);
        try (var connection = clientFactory.connect(server)) {
            var tools = connection.listTools();
            serverRepository.update(server.withLastTest("ok", "connection ok, discovered " + tools.size() + " tools", Instant.now()));
            return McpConnectionTestResult.success(tools.size());
        } catch (Exception exception) {
            var message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            serverRepository.update(server.withLastTest("failed", message, Instant.now()));
            return McpConnectionTestResult.failure(message);
        }
    }

    // ---------- Tool 发现 ----------

    public List<McpToolDefinition> discoverTools(Long serverId) {
        var server = requireServer(serverId);
        try (var connection = clientFactory.connect(server)) {
            var infos = connection.listTools();
            var discovered = new java.util.ArrayList<McpToolDefinition>();
            for (McpToolInfo info : infos) {
                discovered.add(toolRepository.upsert(new McpToolDefinition(
                        null, serverId, info.name(), info.description(), info.inputSchema(),
                        info.readOnlyHint(), McpToolPermissionPolicy.defaultPermission(info), Instant.now())));
            }
            toolRepository.deleteByServerIdNotIn(serverId,
                    infos.stream().map(McpToolInfo::name).toList());
            return discovered;
        } catch (Exception exception) {
            throw new IllegalArgumentException("tool discovery failed for server " + server.name() + ": "
                    + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()), exception);
        }
    }

    public List<McpToolDefinition> listTools(Long serverId) {
        requireServer(serverId);
        return toolRepository.findByServerId(serverId);
    }

    // ---------- 权限 ----------

    public void updateToolPermission(Long serverId, String toolName, McpToolPermission permission) {
        requireServer(serverId);
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName is required");
        }
        if (permission == null) {
            throw new IllegalArgumentException("permission is required");
        }
        if (toolRepository.findByServerAndName(serverId, toolName) == null) {
            throw new IllegalArgumentException("MCP tool does not exist: " + toolName);
        }
        toolRepository.updatePermission(serverId, toolName, permission);
    }

    // ---------- 审计 ----------

    public List<McpToolCallAudit> listToolCallsByRun(String runId) {
        return runId == null || runId.isBlank() ? List.of() : auditRepository.findByRunId(runId);
    }

    public List<McpToolCallAudit> listToolCallsByServer(Long serverId) {
        return auditRepository.findByServerId(serverId);
    }

    // ---------- 内部工具 ----------

    private void validateEndpoint(McpTransportType transportType, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint is required for transport " + transportType);
        }
        if (transportType == McpTransportType.STDIO) {
            stdioCommandPolicy.validate(endpoint.trim());
        }
    }

    private McpServer requireServer(Long id) {
        var server = id == null ? null : serverRepository.findById(id);
        if (server == null) {
            throw new IllegalArgumentException("MCP server does not exist: " + id);
        }
        return server;
    }

    private void requireActiveServer(Long id) {
        var server = requireServer(id);
        if (!server.isActive()) {
            throw new IllegalArgumentException("MCP server is disabled: " + server.name());
        }
    }
}
