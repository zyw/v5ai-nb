package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.ModelUsage;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = ModelUsage.class)
public class ModelUsageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
