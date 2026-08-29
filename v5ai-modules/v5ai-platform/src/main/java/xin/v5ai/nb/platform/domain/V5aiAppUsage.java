package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

/**
 * 应用按日用量（v5ai_app_usage）。
 * <p>表主键为复合键 (agent_key, usage_date)，无单列主键：
 * 不要使用 updateById/deleteById/selectById（MyBatis-Plus 需要单列 @TableId，
 * 强行标注单列会导致 WHERE 只匹配 agent_key 而误改/误删多行），
 * 写入请走 {@code V5aiAppUsageMapper#upsertUsage} 或按复合键条件更新。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_app_usage")
public class V5aiAppUsage extends BaseEntity {

    @TableField("agent_key")
    private String agentKey;
    @TableField("usage_date")
    private LocalDate usageDate;
    @TableField("model_calls")
    private Integer modelCalls;
    private Long tokens;
}
