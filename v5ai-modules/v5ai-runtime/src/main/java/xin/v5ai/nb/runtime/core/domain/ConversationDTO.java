package xin.v5ai.nb.runtime.core.domain;

import java.time.OffsetDateTime;

/**
 * 会话实体（不可变 record）：一段对话的容器，标识它归属哪个 Agent、哪个调用方。
 *
 * <p>归属主体是 API Key 而不是用户（见 docs/adr/0006-api-key-scoped-conversations.md）：
 * {@code apiKeyId} 是租户边界，{@code userId} 只作展示与审计冗余，不参与任何归属判定。</p>
 *
 * @param conversationId 会话 ID：唯一标识一段多轮对话（由调用方生成并复用）
 * @param agentKey       Agent 标识：该会话所属的 Agent
 * @param apiKeyId       归属的 API Key；调试入口产生的会话为 null（不属于任何 Key）
 * @param userId         归属用户（展示/审计冗余）
 * @param name           会话名称；null 表示未命名
 * @param archivedAt     归档时间；非空表示已归档
 * @param createdAt      创建时间
 * @param updatedAt      最近活跃时间（列表按它倒序）
 */
public record ConversationDTO(
        String conversationId,
        String agentKey,
        Long apiKeyId,
        Long userId,
        String name,
        OffsetDateTime archivedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * 是否已归档：归档后列表默认隐藏，且禁止继续对话。
     */
    public boolean archived() {
        return archivedAt != null;
    }
}
