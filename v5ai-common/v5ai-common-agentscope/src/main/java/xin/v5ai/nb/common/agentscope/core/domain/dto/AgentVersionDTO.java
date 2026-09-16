package xin.v5ai.nb.common.agentscope.core.domain.dto;

/**
 * AgentDTO 版本快照（领域契约）：发布时将 AgentDTO 配置固化为不可变版本，供运行时按版本恢复与追溯。
 *
 * @param agentKey     AgentDTO 对外运行标识
 * @param version      递增版本号（从 1 开始，每次发布 +1）
 * @param snapshotJson 该版本固化后的 AgentDTO 配置 JSON 快照
 * @param description  版本说明
 */
public record AgentVersionDTO(String agentKey, long version, String snapshotJson, String description) {
}
