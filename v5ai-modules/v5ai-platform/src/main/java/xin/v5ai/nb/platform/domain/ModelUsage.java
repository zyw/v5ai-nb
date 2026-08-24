package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("v5ai_model_usage")
public class ModelUsage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String runId;
    private String agentKey;
    private String modelKey;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Long durationMs;
    private String status;
    private OffsetDateTime createdAt;
}
