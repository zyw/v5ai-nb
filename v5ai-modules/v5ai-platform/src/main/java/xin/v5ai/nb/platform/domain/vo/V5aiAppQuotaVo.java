package xin.v5ai.nb.platform.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.platform.domain.V5aiAppQuota;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = V5aiAppQuota.class)
public class V5aiAppQuotaVo implements Serializable {

    private String agentKey;
    private Integer dailyModelCalls;
    private Long dailyTokens;
    private Integer ratePerMinute;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
