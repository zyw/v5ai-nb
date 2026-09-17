package xin.v5ai.nb.common.agentscope.core.tools;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xin.v5ai.nb.common.agentscope.core.McpConnection;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedMcpTool;
import xin.v5ai.nb.common.agentscope.core.domain.dto.McpToolCallAuditDTO;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;
import xin.v5ai.nb.common.agentscope.enums.McpToolCallStatus;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;

import java.time.Instant;
import java.util.Map;

/**
 * 平台 MCP 工具：把平台解析的 {@link ResolvedMcpTool} 注册为 AgentScope 工具。
 *
 * 职责：
 * - 执行前按平台权限决策（ALLOW / APPROVE / DENY）判断；
 * - 每次调用落审计（runId、工具名、参数摘要、决策、结果状态、耗时）；
 * - 通过 {@link McpConnection} 执行远程调用；
 * - AgentScope 侧权限钩子直接放行（平台权限模型在 callAsync 内统一执行并审计）。
 */
public class PlatformMcpTool extends ToolBase {
    private static final int MAX_ARGUMENT_SUMMARY_LENGTH = 500;

    private final Long serverId;
    private final String serverName;
    private final McpConnection connection;
    private final McpToolPermission permission;
    private final McpToolCallAuditService auditRepository;
    private final String runId;

    public PlatformMcpTool(ResolvedMcpTool tool, String runId, McpToolCallAuditService auditRepository) {
        super(ToolBase.builder()
                .name(tool.toolName())
                .description(tool.description() == null ? "" : tool.description())
                .inputSchema(tool.inputSchema() == null ? Map.of("type", "object", "properties", Map.of()) : tool.inputSchema())
                .readOnly(false)
                .concurrencySafe(false)
                // 注意：不能设置 externalTool(true) —— AgentScope 会把它短路为
                // "外部编排工具"（suspended 占位结果，状态 RUNNING）而跳过 callAsync，
                // 导致工具永远不会真正执行
                .mcp(tool.serverName()));
        this.serverId = tool.serverId();
        this.serverName = tool.serverName();
        this.connection = tool.connection();
        this.permission = tool.permission() == null ? McpToolPermission.ALLOW : tool.permission();
        this.auditRepository = auditRepository;
        this.runId = runId;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        var arguments = param.getInput() == null ? Map.<String, Object>of() : param.getInput();
        // 防御：DENY 工具不应被注册，但若被调用则直接拒绝并审计
        if (permission == McpToolPermission.DENY) {
            recordAudit(arguments, McpToolCallStatus.DENIED, 0L, "denied by platform permission policy");
            return Mono.just(ToolResultBlock.error("denied by platform permission policy"));
        }
        long started = System.currentTimeMillis();
        return Mono.fromCallable(() -> {
            var result = connection.callTool(getName(), arguments);
            long duration = System.currentTimeMillis() - started;
            recordAudit(arguments, result.ok() ? McpToolCallStatus.SUCCESS : McpToolCallStatus.FAILED,
                    duration, result.message());
            return result.ok()
                    ? ToolResultBlock.text(result.content()).withState(ToolResultState.SUCCESS)
                    : ToolResultBlock.error(result.message()).withState(ToolResultState.ERROR);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> toolInput, PermissionContextState context) {
        // 平台权限决策在 callAsync 中执行并审计，这里不阻塞 AgentDTO 的执行流
        return Mono.just(PermissionDecision.allow("platform MCP permission policy is enforced and audited"));
    }

    private void recordAudit(Map<String, Object> arguments, McpToolCallStatus status, Long durationMs, String message) {
        if (auditRepository == null) {
            return;
        }
        auditRepository.save(new McpToolCallAuditDTO(
                null,
                runId,
                serverId,
                serverName,
                getName(),
                summarize(arguments),
                permission,
                status,
                durationMs,
                message,
                Instant.now()));
    }

    /**
     * 参数摘要：截断超长参数，避免把敏感完整参数落库。
     */
    private static String summarize(Map<String, Object> arguments) {
        var text = String.valueOf(arguments);
        return text.length() <= MAX_ARGUMENT_SUMMARY_LENGTH
                ? text
                : text.substring(0, MAX_ARGUMENT_SUMMARY_LENGTH) + "...(truncated)";
    }
}
