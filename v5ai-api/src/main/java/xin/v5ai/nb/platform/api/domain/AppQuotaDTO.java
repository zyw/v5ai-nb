package xin.v5ai.nb.platform.api.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppQuotaDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用标识
     */
    private String agentKey;
    /**
     * 每日模型调用次数
     */
    private int dailyModelCalls;
    /**
     * 每日令牌数
     */
    private long dailyTokens;
    /**
     * 每分钟速率
     */
    private int ratePerMinute;
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
