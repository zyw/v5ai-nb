package xin.v5ai.nb.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.agentscope.core.McpConnection;
import xin.v5ai.nb.common.agentscope.utils.McpJsonCodec;
import xin.v5ai.nb.common.agentscope.core.StdioCommandPolicy;
import xin.v5ai.nb.common.agentscope.core.domain.McpToolInfo;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;
import xin.v5ai.nb.common.agentscope.enums.McpTransportType;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.mcp.core.McpClientFactory;
import xin.v5ai.nb.mcp.core.McpToolPermissionPolicy;
import xin.v5ai.nb.mcp.core.domain.McpConnectionTestResult;
import xin.v5ai.nb.mcp.core.domain.dto.McpServerDTO;
import xin.v5ai.nb.mcp.core.service.McpServerService;
import xin.v5ai.nb.mcp.domain.AgentMcpBinding;
import xin.v5ai.nb.mcp.domain.McpServer;
import xin.v5ai.nb.mcp.domain.McpTool;
import xin.v5ai.nb.mcp.domain.McpToolCallAudit;
import xin.v5ai.nb.mcp.domain.bo.McpServerBo;
import xin.v5ai.nb.mcp.domain.vo.McpServerVo;
import xin.v5ai.nb.mcp.domain.vo.McpToolCallAuditVo;
import xin.v5ai.nb.mcp.domain.vo.McpToolVo;
import xin.v5ai.nb.mcp.mapper.AgentMcpBindingMapper;
import xin.v5ai.nb.mcp.mapper.McpServerMapper;
import xin.v5ai.nb.mcp.mapper.McpToolCallAuditMapper;
import xin.v5ai.nb.mcp.mapper.McpToolMapper;
import xin.v5ai.nb.mcp.service.IMcpServerService;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * MCP Server 管理服务实现类。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements IMcpServerService, McpServerService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final McpServerMapper serverMapper;
    private final McpToolMapper toolMapper;
    private final McpToolCallAuditMapper auditMapper;
    private final AgentMcpBindingMapper bindingMapper;
    private final McpClientFactory clientFactory;
    private final StdioCommandPolicy stdioCommandPolicy;
    private final CredentialCipher cipher;

    @Override
    public PageResult<McpServerVo> queryPageList(McpServerBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<McpServer> lqw = buildQueryWrapper(bo);
        Page<McpServer> page = pageQuery.build();
        List<McpServer> list = serverMapper.selectList(page, lqw);
        List<McpServerVo> vos = list.stream().map(this::toVo).toList();
        return PageResult.build(vos, page.getTotal());
    }

    @Override
    public List<McpServerVo> queryList(McpServerBo bo) {
        return serverMapper.selectList(buildQueryWrapper(bo)).stream().map(this::toVo).toList();
    }

    @Override
    public List<OptionDTO> queryOptionList() {
        return serverMapper.selectList(QueryBuilder.lambda(McpServer.class)
                        .eq(McpServer::getStatus, STATUS_ACTIVE)
                        .orderByAsc(McpServer::getId)
                        .build())
                .stream()
                .map(server -> new OptionDTO(server.getId(), server.getName() + " (#" + server.getId() + ")"))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerVo createServer(McpServerBo bo) {
        if (bo.getName() == null || bo.getName().isBlank()) {
            throw new IllegalArgumentException("MCP server name is required");
        }
        if (bo.getTransportType() == null) {
            throw new IllegalArgumentException("transport type is required");
        }
        validateEndpoint(bo.getTransportType(), bo.getEndpoint());
        var server = new McpServer();
        server.setName(bo.getName().trim());
        server.setTransportType(bo.getTransportType().name());
        server.setEndpoint(bo.getEndpoint() == null ? null : bo.getEndpoint().trim());
        server.setArgsJson(McpJsonCodec.toJson(bo.getArgs()));
        server.setHeadersCiphertext(encrypt(bo.getHeaders()));
        server.setEnvCiphertext(encrypt(bo.getEnvVars()));
        server.setTimeoutSeconds(bo.getTimeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : bo.getTimeoutSeconds());
        server.setStatus(STATUS_ACTIVE);
        serverMapper.insert(server);
        return toVo(server);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerVo updateServer(McpServerBo bo) {
        if (bo.getId() == null) {
            throw new IllegalArgumentException("server id is required");
        }
        var existing = requireServer(bo.getId());
        var transportType = bo.getTransportType() == null
                ? McpTransportType.valueOf(existing.getTransportType())
                : bo.getTransportType();
        var endpoint = bo.getEndpoint() == null ? existing.getEndpoint() : bo.getEndpoint().trim();
        if (bo.getEndpoint() != null) {
            validateEndpoint(transportType, endpoint);
        }
        var update = new McpServer();
        update.setId(existing.getId());
        update.setName(bo.getName() == null || bo.getName().isBlank() ? existing.getName() : bo.getName().trim());
        update.setTransportType(transportType.name());
        update.setEndpoint(endpoint);
        update.setArgsJson(bo.getArgs() == null ? existing.getArgsJson() : McpJsonCodec.toJson(bo.getArgs()));
        update.setHeadersCiphertext(bo.getHeaders() == null ? existing.getHeadersCiphertext() : encrypt(bo.getHeaders()));
        update.setEnvCiphertext(bo.getEnvVars() == null ? existing.getEnvCiphertext() : encrypt(bo.getEnvVars()));
        update.setTimeoutSeconds(bo.getTimeoutSeconds() == null ? existing.getTimeoutSeconds() : bo.getTimeoutSeconds());
        update.setStatus(existing.getStatus());
        update.setLastTestStatus(existing.getLastTestStatus());
        update.setLastTestMessage(existing.getLastTestMessage());
        update.setLastTestedAt(existing.getLastTestedAt());
        serverMapper.updateById(update);
        return toVo(update);
    }

    @Override
    public boolean disableServer(Long id) {
        requireServer(id);
        var update = new McpServer();
        update.setId(id);
        update.setStatus(STATUS_DISABLED);
        return serverMapper.updateById(update) > 0;
    }

    @Override
    public boolean enableServer(Long id) {
        requireServer(id);
        var update = new McpServer();
        update.setId(id);
        update.setStatus(STATUS_ACTIVE);
        return serverMapper.updateById(update) > 0;
    }

    @Override
    public McpServerVo getServer(Long id) {
        return toVo(requireServer(id));
    }

    @Override
    public McpConnectionTestResult testConnection(Long serverId) {
        var server = requireServer(serverId);
        try (McpConnection connection = clientFactory.connect(toDto(server))) {
            var tools = connection.listTools();
            recordLastTest(serverId, "ok", "connection ok, discovered " + tools.size() + " tools");
            return McpConnectionTestResult.success(tools.size());
        } catch (Exception exception) {
            var message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            recordLastTest(serverId, "failed", message);
            return McpConnectionTestResult.failure(message);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<McpToolVo> discoverTools(Long serverId) {
        var server = requireServer(serverId);
        try (McpConnection connection = clientFactory.connect(toDto(server))) {
            var infos = connection.listTools();
            for (McpToolInfo info : infos) {
                upsertTool(serverId, info);
            }
            toolMapper.deleteByServerIdNotIn(serverId, infos.stream().map(McpToolInfo::name).toList());
            return listTools(serverId);
        } catch (Exception exception) {
            throw new IllegalArgumentException("tool discovery failed for server " + server.getName() + ": "
                    + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()), exception);
        }
    }

    @Override
    public List<McpToolVo> listTools(Long serverId) {
        requireServer(serverId);
        return toolMapper.selectList(QueryBuilder.lambda(McpTool.class)
                        .eq(McpTool::getServerId, serverId)
                        .orderByAsc(McpTool::getToolName)
                        .build())
                .stream()
                .map(this::toToolVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpToolVo updateToolPermission(Long serverId, String toolName, McpToolPermission permission) {
        requireServer(serverId);
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName is required");
        }
        if (permission == null) {
            throw new IllegalArgumentException("permission is required");
        }
        var existing = toolMapper.selectOne(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getServerId, serverId)
                .eq(McpTool::getToolName, toolName));
        if (existing == null) {
            throw new IllegalArgumentException("MCP tool does not exist: " + toolName);
        }
        var update = new McpTool();
        update.setId(existing.getId());
        update.setPermission(permission.name());
        toolMapper.updateById(update);
        existing.setPermission(permission.name());
        return toToolVo(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindServers(String agentKey, List<Long> mcpServerIds) {
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey is required");
        }
        if (mcpServerIds == null) {
            throw new IllegalArgumentException("mcpServerIds is required");
        }
        for (Long serverId : mcpServerIds) {
            requireActiveServer(serverId);
        }
        bindingMapper.delete(new LambdaQueryWrapper<AgentMcpBinding>()
                .eq(AgentMcpBinding::getAgentKey, agentKey));
        for (Long serverId : mcpServerIds) {
            var binding = new AgentMcpBinding();
            binding.setAgentKey(agentKey);
            binding.setMcpServerId(serverId);
            bindingMapper.insert(binding);
        }
    }

    @Override
    public List<Long> getServerBindings(String agentKey) {
        return bindingMapper.selectList(new LambdaQueryWrapper<AgentMcpBinding>()
                        .eq(AgentMcpBinding::getAgentKey, agentKey))
                .stream()
                .map(AgentMcpBinding::getMcpServerId)
                .toList();
    }

    @Override
    public List<McpToolCallAuditVo> listToolCallsByRun(String runId) {
        return runId == null || runId.isBlank() ? List.of() : auditMapper.selectList(
                        new LambdaQueryWrapper<McpToolCallAudit>()
                                .eq(McpToolCallAudit::getRunId, runId)
                                .orderByDesc(McpToolCallAudit::getId))
                .stream()
                .map(this::toAuditVo)
                .toList();
    }

    @Override
    public List<McpToolCallAuditVo> listToolCallsByServer(Long serverId) {
        return auditMapper.selectList(new LambdaQueryWrapper<McpToolCallAudit>()
                        .eq(McpToolCallAudit::getServerId, serverId)
                        .orderByDesc(McpToolCallAudit::getId))
                .stream()
                .map(this::toAuditVo)
                .toList();
    }

    // ---------- 内部工具 ----------

    private LambdaQueryWrapper<McpServer> buildQueryWrapper(McpServerBo bo) {
        return QueryBuilder.lambda(McpServer.class)
                .likeIfText(McpServer::getName, bo == null ? null : bo.getName())
                .eqIfText(McpServer::getTransportType,
                        bo == null || bo.getTransportType() == null ? null : bo.getTransportType().name())
                .eqIfText(McpServer::getStatus, bo == null ? null : bo.getStatus())
                .orderByAsc(McpServer::getId)
                .build();
    }

    private void upsertTool(Long serverId, McpToolInfo info) {
        var existing = toolMapper.selectOne(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getServerId, serverId)
                .eq(McpTool::getToolName, info.name()));
        if (existing == null) {
            var tool = new McpTool();
            tool.setServerId(serverId);
            tool.setToolName(info.name());
            tool.setDescription(info.description());
            tool.setInputSchemaJson(McpJsonCodec.toJson(info.inputSchema()));
            tool.setReadOnly(info.readOnlyHint());
            tool.setPermission(McpToolPermissionPolicy.defaultPermission(info).name());
            tool.setLastDiscoveredAt(OffsetDateTime.now());
            toolMapper.insert(tool);
        } else {
            var update = new McpTool();
            update.setId(existing.getId());
            update.setDescription(info.description());
            update.setInputSchemaJson(McpJsonCodec.toJson(info.inputSchema()));
            update.setReadOnly(info.readOnlyHint());
            update.setLastDiscoveredAt(OffsetDateTime.now());
            // 保留管理员已调整的权限
            toolMapper.updateById(update);
        }
    }

    private void recordLastTest(Long serverId, String status, String message) {
        var update = new McpServer();
        update.setId(serverId);
        update.setLastTestStatus(status);
        update.setLastTestMessage(message);
        update.setLastTestedAt(OffsetDateTime.now());
        serverMapper.updateById(update);
    }

    private void validateEndpoint(McpTransportType transportType, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint is required for transport " + transportType);
        }
        if (transportType == McpTransportType.STDIO) {
            stdioCommandPolicy.validate(endpoint.trim());
        }
    }

    private McpServer requireServer(Long id) {
        var server = id == null ? null : serverMapper.selectById(id);
        if (server == null) {
            throw new IllegalArgumentException("MCP server does not exist: " + id);
        }
        return server;
    }

    private void requireActiveServer(Long id) {
        var server = requireServer(id);
        if (!STATUS_ACTIVE.equals(server.getStatus())) {
            throw new IllegalArgumentException("MCP server is disabled: " + server.getName());
        }
    }

    private String encrypt(java.util.Map<String, String> plaintext) {
        return plaintext == null ? null : cipher.encrypt(McpJsonCodec.toJson(plaintext));
    }

    private McpServerDTO toDto(McpServer server) {
        return buildMcpServerDTO(server);
    }

    private McpServerVo toVo(McpServer server) {
        var vo = new McpServerVo();
        vo.setId(server.getId());
        vo.setName(server.getName());
        vo.setTransportType(server.getTransportType());
        vo.setEndpoint(server.getEndpoint());
        vo.setArgs(McpJsonCodec.toStringList(server.getArgsJson()));
        vo.setHeaders(McpJsonCodec.toStringMap(decrypt(server.getHeadersCiphertext())));
        vo.setEnvVars(McpJsonCodec.toStringMap(decrypt(server.getEnvCiphertext())));
        vo.setTimeoutSeconds(server.getTimeoutSeconds());
        vo.setStatus(server.getStatus());
        vo.setLastTestStatus(server.getLastTestStatus());
        vo.setLastTestMessage(server.getLastTestMessage());
        vo.setLastTestedAt(server.getLastTestedAt());
        vo.setCreatedAt(server.getCreatedAt());
        vo.setUpdatedAt(server.getUpdatedAt());
        return vo;
    }

    private McpToolVo toToolVo(McpTool tool) {
        var vo = new McpToolVo();
        vo.setId(tool.getId());
        vo.setServerId(tool.getServerId());
        vo.setToolName(tool.getToolName());
        vo.setDescription(tool.getDescription());
        vo.setInputSchema(McpJsonCodec.toObjectMap(tool.getInputSchemaJson()));
        vo.setReadOnly(tool.getReadOnly());
        vo.setPermission(tool.getPermission());
        vo.setLastDiscoveredAt(tool.getLastDiscoveredAt());
        vo.setCreatedAt(tool.getCreatedAt());
        vo.setUpdatedAt(tool.getUpdatedAt());
        return vo;
    }

    private McpToolCallAuditVo toAuditVo(McpToolCallAudit audit) {
        var vo = new McpToolCallAuditVo();
        vo.setId(audit.getId());
        vo.setRunId(audit.getRunId());
        vo.setServerId(audit.getServerId());
        vo.setServerName(audit.getServerName());
        vo.setToolName(audit.getToolName());
        vo.setArgumentsSummary(audit.getArgumentsSummary());
        vo.setDecision(audit.getDecision());
        vo.setStatus(audit.getStatus());
        vo.setDurationMs(audit.getDurationMs());
        vo.setMessage(audit.getMessage());
        vo.setCreatedAt(audit.getCreatedAt());
        return vo;
    }

    private String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        try {
            return cipher.decrypt(ciphertext);
        } catch (Exception exception) {
            log.warn("failed to decrypt mcp server secret: {}", exception.getMessage());
            return null;
        }
    }

    @Override
    public McpServerDTO selectById(Long id) {
        McpServer entity = serverMapper.selectById(id);
        return entity == null ? null : buildMcpServerDTO(entity);
    }

}
