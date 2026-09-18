package xin.v5ai.nb.platform.api.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * API Key 鉴权结果：密钥归属与该 Key 可访问的 Agent 集合。
 *
 * @param apiKeyId  密钥主键
 * @param userId    归属用户（plm_user.id）
 * @param name      Key 名称
 * @param trackingId 跟踪 ID（独立 UUID，与明文 Key 无关联，仅供展示/审计）
 * @param enabled   是否启用
 * @param agentKeys 该 Key 绑定（可访问）的 agentKey 集合
 */
public record ApiKeysAuthDTO(
        Long apiKeyId,
        Long userId,
        String name,
        String trackingId,
        boolean enabled,
        Set<String> agentKeys) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否允许访问指定 Agent。
     *
     * @param agentKey 运行期路径中的 Agent 标识
     * @return 已启用且该 agentKey 在绑定集合内时返回 true
     */
    public boolean allows(String agentKey) {
        return enabled && agentKey != null && agentKeys != null && agentKeys.contains(agentKey);
    }
}