package xin.v5ai.nb.common.agentscope.enums;

/**
 * Tool 调用权限三态决策：允许 / 人工审批（运行时发出 PERMISSION_REQUIRED 事件后自动放行）/ 拒绝。
 */
public enum McpToolPermission {
    ALLOW,
    APPROVE,
    DENY
}
