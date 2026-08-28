package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.V5aiAppUsage;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = V5aiAppUsage.class)
public class V5aiAppUsageVo implements Serializable {

    private String agentKey;
    private LocalDate usageDate;
    private Integer modelCalls;
    private Long tokens;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
