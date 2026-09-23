package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.V5aiApiKeys;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * API Key 列表项。明文 Key 不落库、不返回，页面只展示 {@code trackingId} 生成的掩码前缀。
 */
@Data
@AutoMapper(target = V5aiApiKeys.class)
public class V5aiApiKeysVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String name;

    /**
     * 跟踪 ID：独立 UUID，与明文 Key 无关，列表直接展示并可复制（日志/审计对账用）
     */
    private String trackingId;

    private Boolean enabled;

    /**
     * 最新使用时间
     */
    private OffsetDateTime lastUsedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /**
     * 可访问的 agentKey 列表（非 v5ai_api_keys 表字段，查询后由服务填充）
     */
    private List<String> agentKeys;
}