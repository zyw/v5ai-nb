package xin.v5ai.nb.platform.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 模型使用记录
 */
@Data
@Builder
public class ModelUsagePageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Long id;
    /**
     * 运行 ID
     */
    private String runId;
    /**
     * 应用标识
     */
    private String agentKey;
    /** 平台模型配置 ID；历史记录可能为空 */
    private Long modelId;
    /**
     * 模型标识
     */
    private String modelKey;
    /**
     * 输入 Token（估算）
     */
    private long promptTokens;
    /**
     * 输出 Token（估算）
     */
    private long completionTokens;
    /**
     * 耗时（毫秒）
     */
    private long durationMs;
    /**
     * 状态
     */
    private String status;
    /**
     * 记录时间
     */
    private OffsetDateTime createdAt;

    /**
     * 总 Token（估算）
     */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
