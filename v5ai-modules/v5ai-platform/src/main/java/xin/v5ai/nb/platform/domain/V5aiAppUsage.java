package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_app_usage")
public class V5aiAppUsage extends BaseEntity {

    @TableId(value = "agent_key")
    private String agentKey;
    @TableField("usage_date")
    private LocalDate usageDate;
    @TableField("model_calls")
    private Integer modelCalls;
    private Long tokens;
}
