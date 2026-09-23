package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_app_quota")
public class V5aiAppQuota extends BaseEntity {
    @TableId
    private String agentKey;
    private Integer dailyModelCalls;
    private Long dailyTokens;
    private Integer ratePerMinute;
}
